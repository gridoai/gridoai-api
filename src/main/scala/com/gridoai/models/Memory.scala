package com.gridoai.models

import cats.effect.IO
import com.gridoai.domain._
import com.gridoai.mock.mockedChunk
import com.gridoai.mock.mockedDocument

import scala.collection.mutable.ListBuffer

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
    MockedDocument(mockedDocument, "org1", "role1")
  )
  private val allChunks = ListBuffer[MockedChunk](
    MockedChunk(mockedChunk, "org1", "role1")
  )

  def addDocument(
      doc: Document,
      orgId: String,
      roles: String
  ): IO[Either[String, Document]] =
    IO.pure {
      allDocuments += MockedDocument(doc, orgId, roles)
      println(s"Mock: Adding document $doc")
      Right(doc)
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
      documentToDelete match {
        case Some(doc) =>
          allDocuments -= doc
          Right(())
        case None =>
          Left("No document was deleted")
      }
    }

  def addChunks(orgId: String, role: String)(
      chunks: List[ChunkWithEmbedding]
  ): IO[Either[String, List[ChunkWithEmbedding]]] =
    IO.pure {
      allChunks ++= chunks.map(chunk => MockedChunk(chunk, orgId, role))
      println(s"Mock: Adding chunks $chunks")
      Right(chunks)
    }

  def getNearChunks(
      embedding: Embedding,
      limit: Int,
      orgId: String,
      role: String
  ): IO[Either[String, List[SimilarChunk]]] =
    IO.pure(
      Right(
        allChunks.toList
          .filter(row => row.orgId == orgId && row.role == role)
          .take(limit)
          .map(x =>
            SimilarChunk(
              chunk = x.chunk.chunk,
              distance = 1
            )
          )
      )
    )

  def deleteChunksByDocument(
      documentUid: UID,
      orgId: String,
      role: String
  ): IO[Either[String, Unit]] =
    IO.pure {
      val chunkToDelete = allChunks
        .filter(row =>
          row.chunk.chunk.documentUid == documentUid && row.orgId == orgId && row.role == role
        )
        .headOption
      chunkToDelete match {
        case Some(chunk) =>
          allChunks -= chunk
          Right(())
        case None =>
          Left("No document was deleted")
      }
    }
