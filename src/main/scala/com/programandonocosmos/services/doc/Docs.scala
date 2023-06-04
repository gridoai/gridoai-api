package com.programandonocosmos.services.doc

import cats.effect.IO
import com.programandonocosmos.adapters.contextHandler.DocumentApiClient
import com.programandonocosmos.domain.Document
import com.programandonocosmos.domain.UID
import com.programandonocosmos.models.DocDB

import java.util.UUID

import util.chaining.scalaUtilChainingOps

def searchDoc(
    x: String
)(implicit db: DocDB[IO]): IO[Either[String, List[Document]]] = {
  println("Searching for: " + x)
  DocumentApiClient.neardocs(x).flatMap { responseEither =>
    responseEither match {
      case Right(response) =>
        val ids = response.message.map(_._1.pipe(UUID.fromString))
        println(f"Searching ids: $ids")
        db.getDocumentsByIds(ids).attempt.map {
          case Right(docs) => Right(docs)
          case Left(err)   => Left(err.getMessage) // Map the error to a string
        }

      case Left(df: io.circe.Error) =>
        IO.pure(Left("Parsing error: " + df.getMessage))
      case Left(other) => IO.pure(Left(other.toString))
    }
  }
}
def createDoc(
    document: Document
)(implicit db: DocDB[IO]): IO[Either[String, Unit]] = {
  db.addDocument(document).attempt.map {
    case Right(_)  => Right(())
    case Left(err) => Left(err.getMessage)
  }
}
