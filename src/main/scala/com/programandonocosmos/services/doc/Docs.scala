package com.programandonocosmos.services.doc

import cats.effect.IO
import com.programandonocosmos.adapters.contextHandler.{
  DocumentApiClient,
  MessageResponse
}
import com.programandonocosmos.domain.Document
import com.programandonocosmos.domain.UID
import com.programandonocosmos.models.DocDB

import java.util.UUID
import util.chaining.scalaUtilChainingOps
import cats.syntax.parallel.*

def searchDoc(
    x: String
)(implicit db: DocDB[IO]): IO[Either[String, List[Document]]] = {
  println("Searching for: " + x)
  DocumentApiClient.neardocs(x).flatMap {
    case Right(response) =>
      val ids = response.message.map(_._1.pipe(UUID.fromString))
      println(f"Searching ids: $ids")
      db.getDocumentsByIds(ids).attempt.map(_.left.map(_.toString))
    case Left(df: io.circe.Error) =>
      IO.pure(Left("Parsing error: " + df.getMessage))
    case Left(other) => IO.pure(Left(other.toString))
  }
}
def createDoc(
    document: Document
)(implicit db: DocDB[IO]): IO[Either[String, Unit]] = {
  println("Creating doc... ")
  (
    db.addDocument(document),
    DocumentApiClient.write(document.uid.toString, document.content)
  ).parTupled.map {
    case (_, Right(_)) => Right(())
    case (_, Left(e))  => Left(e.toString)
  }
}
