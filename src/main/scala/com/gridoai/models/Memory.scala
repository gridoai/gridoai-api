package com.gridoai.models

import cats.effect.IO
import com.gridoai.domain._
import com.gridoai.mock.mockedChunk
import com.gridoai.mock.mockedDocument

import scala.collection.mutable.ListBuffer
import com.gridoai.utils.mapRight

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

  def deleteDocumentsBySource(
      sources: List[Source],
      orgId: String,
      role: String
  ): IO[Either[String, Unit]] =
    IO.pure {
      val documentsToDelete = allDocuments
        .filter(row =>
          sources.contains(
            row.doc.source
          ) && row.orgId == orgId && row.role == role
        )
      val chunksToDelete = allChunks
        .filter(row =>
          sources.contains(
            row.chunk.chunk.documentSource
          ) && row.orgId == orgId && row.role == role
        )
      allDocuments --= documentsToDelete
      allChunks --= chunksToDelete
      Right(())
    }

  def getNearChunks(
      embedding: Embedding,
      offset: Int,
      limit: Int,
      orgId: String,
      role: String
  ): IO[Either[String, List[SimilarChunk]]] =
    IO.pure(
      Right(
        allChunks.toList
          .filter(row => row.orgId == orgId && row.role == role)
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
