package com.gridoai.models

import cats.effect.IO
import com.gridoai.domain._
import com.gridoai.mock.mockedDoc

import scala.collection.mutable.ListBuffer

object MockDocDB extends DocDB[IO]:
  private val documents = ListBuffer[DocumentWithEmbedding](
    mockedDoc
  )
  def listDocuments(
      orgId: String,
      role: String,
      limit: Int,
      page: Int
  ): IO[Either[String, List[Document]]] =
    IO.pure(Right(documents.toList.map(_.document)))

  def addDocument(
      doc: DocumentWithEmbedding,
      orgId: String,
      roles: String
  ): IO[Either[String, String]] =
    IO.pure {
      documents += doc
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
          .take(limit)
          .map(x =>
            SimilarDocument(
              document = x.document,
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
      val documentToDelete = documents.filter(_.document.uid == uid).head
      Right(documents -= documentToDelete)
    }
