package com.gridoai.models

import cats.effect.IO
import com.gridoai.domain._
import com.gridoai.mock.mockedChunk
import com.gridoai.mock.mockedDocument

import scala.collection.mutable.ListBuffer
import com.gridoai.utils.mapRight
import scala.annotation.unused

case class MockedDocument(
    doc: Document,
    orgId: String,
    role: String
)

case class MockedChunk(
    chunk: ChunkWithEmbedding,
    orgId: String,
    role: String
)

object MockDocDB extends DocDB[IO]:
  private val allDocuments = ListBuffer[MockedDocument](
    MockedDocument(mockedDocument, "org1", "admin")
  )
  private val allChunks = ListBuffer[MockedChunk](
    MockedChunk(mockedChunk, "org1", "admin")
  )
  def addDocument(
      doc: com.gridoai.models.DocumentPersistencePayload,
      orgId: String,
      role: String
  ) = addDocuments(List(doc), orgId, role).mapRight(_.head)

  def addDocuments(
      docs: List[com.gridoai.models.DocumentPersistencePayload],
      orgId: String,
      role: String
  ) =
    IO.pure {
      allDocuments ++= docs.map(doc => MockedDocument(doc.doc, orgId, role))
      allChunks ++= docs.flatMap(
        _.chunks.map(chunk => MockedChunk(chunk, orgId, role))
      )
      println(s"Mock: Adding documents $docs")
      Right(docs.map(_.doc))
    }

  def listDocuments(
      orgId: String,
      role: String,
      start: Int,
      end: Int
  ): IO[Either[String, PaginatedResponse[List[Document]]]] =
    IO.pure(
      Right(
        PaginatedResponse(
          allDocuments.toList
            .filter(row => row.orgId == orgId && row.role == role)
            .map(_.doc)
            .slice(start, end),
          allDocuments.length
        )
      )
    )

  def deleteDocument(
      uid: UID,
      orgId: String,
      role: String
  ): IO[Either[String, Unit]] =
    IO.pure {
      val documentToDelete = allDocuments
        .filter(row =>
          row.doc.uid == uid && row.orgId == orgId && row.role == role
        )
        .headOption
      val chunkToDelete = allChunks
        .filter(row =>
          row.chunk.chunk.documentUid == uid && row.orgId == orgId && row.role == role
        )
        .headOption
      (documentToDelete, chunkToDelete) match {
        case (Some(doc), Some(chunk)) =>
          allDocuments -= doc
          allChunks -= chunk
          Right(())
        case _ =>
          Left("No document or no chunk was deleted")
      }
    }

  def listDocumentsBySource(
      sources: List[Source],
      orgId: String,
      role: String
  ): IO[Either[String, List[Document]]] =
    IO.pure(
      Right(
        allDocuments.toList
          .filter(row =>
            row.orgId == orgId && row.role == role && sources.contains(
              row.doc.source
            )
          )
          .map(_.doc)
      )
    )
  def getNearChunks(
      embeddings: List[Embedding],
      scope: Option[List[UID]],
      offset: Int,
      limit: Int,
      orgId: String,
      role: String
  ): IO[Either[String, List[List[SimilarChunk]]]] =
    val scopeFilter = scope match
      case Some(uids) => (uid => uids.contains(uid))
      case None =>
        def f(@unused _uid: UID): Boolean = true
        f

    IO.pure(
      Right(
        embeddings.map: embedding =>
          allChunks.toList
            .filter(row =>
              row.orgId == orgId && row.role == role && scopeFilter(
                row.chunk.chunk.uid
              )
            )
            .drop(offset)
            .take(limit)
            .map(x =>
              SimilarChunk(
                chunk = x.chunk.chunk,
                distance = 1
              )
            )
      )
    )
