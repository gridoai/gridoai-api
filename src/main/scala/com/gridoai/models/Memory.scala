package com.gridoai.models

import cats.effect.IO
import com.gridoai.domain._
import com.gridoai.mock.mockedDoc

import scala.collection.mutable.ListBuffer

case class MockedRow(
    doc: DocumentWithEmbedding,
    orgId: String,
    role: String
)

object MockDocDB extends DocDB[IO]:
  private val documents = ListBuffer[MockedRow](
    MockedRow(mockedDoc, "org1", "role1")
  )

  def listDocuments(
      orgId: String,
      role: String,
      start: Int,
      end: Int
  ) =
    IO.pure(
      Right(
        documents.toList
          .filter(row => row.orgId == orgId && row.role == role)
          .map(_.doc.document)
          .slice(start, end),
        documents.length
      )
    )

  def addDocument(
      doc: DocumentWithEmbedding,
      orgId: String,
      roles: String
  ): IO[Either[String, String]] =
    IO.pure {
      documents += MockedRow(doc, orgId, roles)
      println(s"Mock: Adding document $doc")
      Right(doc.document.uid.toString())
    }

  def getNearDocuments(
      embedding: Embedding,
      limit: Int,
      orgId: String,
      role: String
  ): IO[Either[String, List[SimilarDocument]]] =
    IO.pure(
      Right(
        documents.toList
          .filter(row => row.orgId == orgId && row.role == role)
          .take(limit)
          .map(x =>
            SimilarDocument(
              document = x.doc.document,
              distance = 1
            )
          )
      )
    )

  def deleteDocument(
      uid: UID,
      orgId: String,
      role: String
  ): IO[Either[String, Unit]] =
    IO.pure {
      val documentToDelete = documents
        .filter(row =>
          row.doc.document.uid == uid && row.orgId == orgId && row.role == role
        )
        .headOption
      documentToDelete match {
        case Some(doc) =>
          documents -= doc
          Right(())
        case None =>
          Left("No document was deleted")
      }
    }
