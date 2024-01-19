package com.gridoai.scripts.reprocChunks

import com.gridoai.models.*
import com.gridoai.domain.*
import com.gridoai.utils.*
import com.gridoai.services.doc.*
import com.gridoai.adapters.embeddingApi.*
import com.github.tototoshi.csv._
import doobie.*
import doobie.implicits.*
import cats.*
import cats.effect.*
import cats.implicits.*
import cats.syntax.list.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import cats.effect.unsafe.implicits.global
import com.pgvector.PGvector
import java.io.File
import java.util.UUID

// get all doc uids, name, content, source, orgId, role
// for each doc
//      upsert doc
def reproc() =
  val xa = PostgresClient.getSyncTransactor
  val docDb = PostgresClient[IO](xa)
  val docs = sql"""
       select uid, name, source, content, organization, roles
       from $documentsTable
       where uid in (select distinct document_uid from $chunksTable where end_pos = 0)
       order by uid asc
     """
    .query[DocRow]
    .to[List]
    .transact[IO](xa)
    .unsafeRunSync()
    .traverse(docRow =>
      docRow.toDocument.map(doc => (doc, docRow.organization, docRow.roles))
    )
    .right
    .get
  println("docs loaded!")
  val chunks = CSVReader
    .open(
      new File(
        "supabase_zwucntyjfcfhxftflyxw_Select chunks from public table.csv"
      )
    )
    .all()
    .tail
    .map(chunkRow =>
      ChunkWithEmbedding(
        chunk = Chunk(
          documentUid = UUID.fromString(chunkRow.get(1).get),
          documentName = chunkRow.get(2).get,
          documentSource = strToSource(chunkRow.get(3).get).right.get,
          uid = UUID.fromString(chunkRow.get(0).get),
          content = chunkRow.get(4).get,
          tokenQuantity = chunkRow.get(9).get.toIntOption.get,
          startPos = chunkRow.get(5).get.toIntOption.get,
          endPos = chunkRow.get(6).get.toIntOption.get
        ),
        embedding = Embedding(
          vector = chunkRow
            .get(7)
            .get
            .stripPrefix("[")
            .stripSuffix("]")
            .split(",")
            .map(_.trim)
            .map(_.toFloat)
            .toList,
          model = strToEmbedding(chunkRow.get(8).get)
        )
      )
    )
  println("chunks loaded!")
  println(s"doc quantity: ${docs.length}")
  docs.map: (doc, org, roles) =>
    if doc.uid != UUID.fromString("cd712080-1953-4e7e-abed-5809f8487c41") then
      println(s"curr doc: ${doc.uid}")
      val relatedChunks = chunks.filter(_.chunk.documentUid == doc.uid)
      println(
        s"related chunks:\n${relatedChunks
            .map(c => c.chunk.content.substring(0, List(20, c.chunk.content.length).min))
            .mkString("\n")}"
      )
      val newChunks = makeChunks(doc).map: newChunk =>
        val oldChunk = relatedChunks
          .filter(_.chunk.content == newChunk.content)
          .head
        val oldChunkButModified = oldChunk.chunk.copy(
          uid = newChunk.uid,
          startPos = newChunk.startPos,
          endPos = newChunk.endPos
        )
        ChunkWithEmbedding(
          chunk = oldChunkButModified,
          embedding = oldChunk.embedding
        )

      docDb
        .addDocuments(
          List(DocumentPersistencePayload(doc = doc, chunks = newChunks)),
          org,
          roles.head
        )
        .value.unsafeRunSync()
