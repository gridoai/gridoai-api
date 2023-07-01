package com.gridoai.services.doc

import cats.effect.IO
import cats.implicits._
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
import com.gridoai.auth.{JWTPayload, getOrgIdAndRolesFromJwt}
import sttp.model.Part
import java.io.File

def searchDoc(auth: JWTPayload)(text: String)(using
    db: DocDB[IO]
): IO[Either[String, List[Document]]] =
  println(s"Searching for: $text")
  val (orgId, roles) = getOrgIdAndRolesFromJwt(auth)
  getEmbeddingAPI("gridoai-ml")
    .embed(text)
    .map(
      _.map(vec =>
        db.getNearDocuments(vec, 5, orgId, roles).map(_.map(_.map(_.document)))
      )
    ) |> flattenIOEitherIOEither

def mapExtractToUploadError(e: ExtractTextError): FileUploadError =
  FileParseError(e.format, e.message)

def extractText(
    file: Part[java.io.File]
): IO[Either[ExtractTextError, String]] = traceMappable("extractText"):
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

def uploadFile(auth: JWTPayload)(file: Part[File])(using db: DocDB[IO]) =
  println(s"Uploading document... ${file.fileName}")
  val (orgId, role) = getOrgIdAndRolesFromJwt(auth)
  extractText(file)
    .map(_.left.map(mapExtractToUploadError))
    .map(extracted =>
      println(
        s"Extracted[${file.fileName}]: ${extracted.map(_.slice(0, 20))} "
      )
      (file.fileName, extracted)
    )
    .flatMap:
      case (Some(filename), Right(content)) =>
        createDoc(auth)(
          DocumentCreationPayload(
            name = filename,
            source = filename,
            content = content,
            orgId,
            role
          )
        )
          .map(_.leftMap(DocumentCreationError.apply))

      case (_, Left(e: FileUploadError)) =>
        IO.pure(Left(e))
      case (None, _) =>
        IO.pure(Left(UnknownError("File extension not known")))

def uploadDocuments(auth: JWTPayload)(
    source: FileUpload
)(using db: DocDB[IO]): IO[Either[List[FileUploadError], Unit]] =
  source.files
    .traverse(uploadFile(auth))
    .map(collectLeftsOrElseUnit)

def createDoc(auth: JWTPayload)(
    payload: DocumentCreationPayload
)(implicit db: DocDB[IO]): IO[Either[String, Unit]] =
  traceMappable("createDoc"):
    println("Creating doc... ")
    val (orgId, roles) = getOrgIdAndRolesFromJwt(auth)
    val document =
      payload.toDocument(UUID.randomUUID(), payload.content.split(" ").length)
    getEmbeddingAPI("gridoai-ml")
      .embed(document.content)
      .map(
        _.map(vec =>
          db.addDocument(DocumentWithEmbedding(document, vec), orgId, roles)
        )
      ) |> flattenIOEitherIOEither

def ask(auth: JWTPayload)(messages: List[Message])(implicit
    db: DocDB[IO]
): IO[Either[String, String]] = traceMappable("ask"):
  val prompt = messages.head.message
  val llm = getLLM("palm2")
  println("Using llm: " + llm.toString())
  messages.last.from match
    case MessageFrom.Bot =>
      IO.pure(Left("Last message should be from the user"))
    case MessageFrom.User =>
      searchDoc(auth)(prompt).flatMap:
        case Right(docs) =>
          val sources = docs.map(_.source).mkString(", ")
          llm
            .ask(docs, messages)
            .map(_.map(x => s"$x\nsources: $sources"))
        case Left(l) => IO.pure(Left(l))
