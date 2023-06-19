package com.gridoai.services.doc

import cats.effect.IO
import cats.syntax.parallel.*
import com.gridoai.adapters.contextHandler.DocumentApiClient
import com.gridoai.adapters.contextHandler.MessageResponse
import com.gridoai.adapters.llm.*
import com.gridoai.domain.*
import com.gridoai.models.DocDB

import java.util.UUID

import util.chaining.scalaUtilChainingOps

def searchDoc(
    x: String
)(implicit db: DocDB[IO]): IO[Either[String, List[Document]]] =
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

def createDoc(
    docInput: DocCreationPayload
)(implicit db: DocDB[IO]): IO[Either[String, Unit]] =
  println("Creating doc... ")
  val document = Document(
    UUID.randomUUID(),
    docInput.name,
    docInput.content,
    docInput.url.getOrElse(docInput.name),
    docInput.content.split(" ").length
  )
  (
    db.addDocument(document).attempt,
    DocumentApiClient.write(document.uid.toString, document.content)
  ).parTupled.map:
    case (Right(()), Right(_)) => Right(())
    case (_, Left(e))          => Left(e.toString)
    case (Left(e), Right(_))   => Left(e.toString)

def ask(messages: List[Message])(implicit
    db: DocDB[IO]
): IO[Either[String, String]] =
  val prompt = messages.head.message
  val llm = getLLM("palm2")
  messages.last.from match
    case MessageFrom.Bot =>
      IO.pure(Left("Last message should be from the user"))
    case MessageFrom.User =>
      searchDoc(prompt).flatMap:
        case Right(r) => llm.ask(r, messages)
        case Left(l)  => IO.pure(Left(l))
