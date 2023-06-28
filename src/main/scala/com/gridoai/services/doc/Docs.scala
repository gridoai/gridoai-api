package com.gridoai.services.doc

import cats.effect.IO
import cats.implicits._
import cats.implicits.catsSyntaxApplicativeId
import com.gridoai.adapters.llm.*
import com.gridoai.adapters.embeddingApi.*
import com.gridoai.domain.*
import com.gridoai.models.DocDB
import com.gridoai.utils.*
import com.gridoai.endpoints.*
import FileUploadError._
import java.nio.file.Files
import com.gridoai.adapters.extractTextFromDocx
import com.gridoai.adapters.extractTextFromPptx
import java.util.UUID
import com.gridoai.parsers.{ExtractTextError}
import com.gridoai.adapters.*
import com.gridoai.parsers.*

def searchDoc(text: String)(using
    db: DocDB[IO]
): IO[Either[String, List[Document]]] =
  println(s"Searching for: $text")
  getEmbeddingAPI("gridoai-ml")
    .embed(text)
    .map(
      _.map(vec =>
        db.getNearDocuments(vec, 5).map(_.map(x => x.map(_.document)))
      )
    ) |> flattenIOEitherIOEither

def mapExtractToUploadError(e: ExtractTextError): FileUploadError =
  FileParseError(e.format, e.message)

def extractText(
    file: sttp.model.Part[java.io.File]
): IO[Either[ExtractTextError, String]] =
  val body = Files.readAllBytes(file.body.toPath)
  val name = file.fileName
  println("file name: " + name)
  println("file content type: " + file.contentType)
  file.contentType match
    case Some("application/pdf") =>
      extractTextFromPdf(body)
    case Some(
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ) =>
      extractTextFromDocx(body).pure[IO]
    case Some(
          "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        ) =>
      extractTextFromPptx(body).pure[IO]
    case Some("text/plain") => IO.pure(Right(String(body)))
    case Some(otherFormat) =>
      Left(
        ExtractTextError(
          FileFormats.Unknown(otherFormat),
          "Unknown file format"
        )
      ).pure[IO]

    case None =>
      IO.pure(
        Left(ExtractTextError(FileFormats.Unknown(""), "Unknown file format"))
      )

def uploadDocuments(
    source: FileUpload
)(using db: DocDB[IO]): IO[List[Either[FileUploadError, Unit]]] =
  source.files.traverse: file =>
    println(s"Uploading document... $file")

    extractText(file)
      .map(_.left.map(mapExtractToUploadError))
      .map(extracted =>
        println(s"Extracted[${file.fileName}]: $extracted ")
        (file.fileName, extracted)
      )
      .flatMap:
        case (Some(filename), Right(content)) =>
          createDoc(
            DocumentCreationPayload(
              name = filename,
              source = filename,
              content = content
            )
          )
            .map(_.leftMap(DocumentCreationError.apply))

        case (_, Left(e: FileUploadError)) =>
          IO.pure(Left(e))
        case (None, _) =>
          IO.pure(Left(UnknownError("File extension not known")))

def createDoc(
    payload: DocumentCreationPayload
)(implicit db: DocDB[IO]): IO[Either[String, Unit]] =
  println("Creating doc... ")
  val document =
    payload.toDocument(UUID.randomUUID(), payload.content.split(" ").length)
  getEmbeddingAPI("gridoai-ml")
    .embed(document.content)
    .map(
      _.map(vec => db.addDocument(DocumentWithEmbedding(document, vec)))
    ) |> flattenIOEitherIOEither

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
        case Right(docs) =>
          val sources = docs.map(_.source).mkString(", ")
          llm
            .ask(docs, messages)
            .map(_.map(x => s"$x\nsources: $sources")) |> attempt
        case Left(l) => IO.pure(Left(l))
