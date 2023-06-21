package com.gridoai.services.doc

import cats.effect.IO
import cats.syntax.parallel.*
import com.gridoai.adapters.contextHandler.DocumentApiClient
import com.gridoai.adapters.contextHandler.MessageResponse
import com.gridoai.adapters.llm.*
import com.gridoai.domain.*
import com.gridoai.models.DocDB
import com.gridoai.utils.*

import java.util.UUID

import util.chaining.scalaUtilChainingOps

def searchDoc(
    x: String
)(implicit db: DocDB[IO]): IO[Either[String, List[Document]]] =
  println("Searching for: " + x)
  DocumentApiClient.neardocs(x).flatMap {
    case Right(response) =>
      IO.pure(
        Right(
          response.message.map(doc =>
            Document(
              uid = UUID.fromString(doc.uid),
              name = doc.path.getOrElse(""),
              url = "",
              content = doc.content,
              numberOfWords = 0
            )
          )
        )
      )
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
    IO.pure(Right(())),
    DocumentApiClient.write(
      document.uid.toString,
      document.content,
      document.name
    )
  ).parTupled.map:
    case (Right(()), Right(_)) => Right(())
    case (_, Left(e))          => Left(e.toString)

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
        case Right(r) =>
          llm.ask(r, messages) |> attempt
        case Left(l) => IO.pure(Left(l))
