package com.gridoai.services.doc

import java.util.UUID
import cats.effect.IO
import cats.implicits._

import com.gridoai.domain.*
import com.gridoai.utils.*
import com.gridoai.adapters.embeddingApi.*
import com.gridoai.models.DocDB

def chunkContent(
    content: String,
    chunkSize: Int,
    overlapSize: Int
): List[String] =
  val words = content.split(" ")

  List
    .range(
      0,
      words.length + 1 - overlapSize,
      chunkSize - overlapSize
    )
    .map(i => words.slice(i, i + chunkSize).mkString(" "))

def makeChunks(document: Document): List[Chunk] =
  chunkContent(document.content, 200, 100).map(content =>
    Chunk(
      documentUid = document.uid,
      documentName = document.name,
      documentSource = document.source,
      uid = UUID.randomUUID(),
      content = content,
      tokenQuantity = content.split(" ").length
    )
  )

def embedChunks(embedding: EmbeddingAPI[IO])(
    chunks: List[Chunk]
): IO[Either[String, List[ChunkWithEmbedding]]] =
  chunks
    .traverse(chunk =>
      embedding
        .embed(chunk.content)
        .mapRight(embed => ChunkWithEmbedding(chunk, embed))
    )
    .map(partitionEithers)
    .mapLeft(x => x.mkString(","))

def addChunks(orgId: String, role: String)(
    chunks: IO[Either[String, List[ChunkWithEmbedding]]]
)(implicit db: DocDB[IO]) =
  chunks

def makeAndStoreChunks(
    embedding: EmbeddingAPI[IO],
    orgId: String,
    role: String
)(document: Document)(implicit db: DocDB[IO]) =
  val embededChunks = makeChunks(document) |> embedChunks(embedding)
  embededChunks.flatMapRight(db.addChunks(orgId, role))
