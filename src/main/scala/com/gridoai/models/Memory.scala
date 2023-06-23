package com.gridoai.models

import cats.effect.IO
import com.gridoai.domain._
import com.gridoai.mock.mockedDoc

import java.util.UUID
import scala.collection.mutable.ListBuffer

object MockDocDB extends DocDB[IO]:
  private val documents = ListBuffer[DocumentWithEmbedding](
    mockedDoc
  )

  def addDocument(doc: DocumentWithEmbedding): IO[Unit] =
    IO.pure {
      documents += doc
      println(s"Mock: Adding document $doc")
    }

  def getNearDocuments(
      embedding: Embedding,
      limit: Int
  ): IO[List[SimilarDocument]] =
    IO.pure(
      documents.toList
        .take(limit)
        .map(x =>
          SimilarDocument(
            uid = x.uid,
            name = x.name,
            source = x.source,
            content = x.content,
            tokenQuantity = x.tokenQuantity,
            embedding = x.embedding,
            similarity = 1
          )
        )
    )

  def deleteDocument(uid: UID): IO[Unit] =
    IO.pure {
      val documentToDelete = documents.filter(_.uid == uid).head
      documents -= documentToDelete
    }
