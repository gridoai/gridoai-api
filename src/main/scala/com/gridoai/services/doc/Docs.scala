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
import sttp.model.Part
import java.io.File
import com.gridoai.auth.limitRole
import com.gridoai.auth.authErrorMsg
import com.gridoai.auth.AuthData

def searchDoc(auth: AuthData)(text: String)(using
    db: DocDB[IO]
): IO[Either[String, List[Document]]] =
  println(s"Searching for: $text")

  getEmbeddingAPI("gridoai-ml")
    .embed(text)
    .flatMapRight(vec =>
      db.getNearDocuments(vec, 5, auth.orgId, auth.role)
        .map(_.map(_.map(_.document)))
    )

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

def uploadFile(auth: AuthData)(file: Part[File])(using db: DocDB[IO]) =
  println(s"Uploading document... ${file.fileName}")

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
            content = content
          )
        )
          .mapLeft(DocumentCreationError.apply)

      case (_, Left(e: FileUploadError)) =>
        IO.pure(Left(e))
      case (None, _) =>
        IO.pure(Left(UnknownError("File extension not known")))

def uploadDocuments(auth: AuthData)(
    source: FileUpload
)(using db: DocDB[IO]): IO[Either[List[FileUploadError], Unit]] =
  limitRole(
    auth.role,
    (Left(List(UnauthorizedError(authErrorMsg(Some(auth.role)))))).pure[IO]
  ):
    source.files
      .traverse(uploadFile(auth))
      .map(collectLeftsOrElseUnit)

def listDocuments(auth: AuthData)(
    start: Int,
    end: Int
)(using db: DocDB[IO]): IO[Either[String, List[Document]]] =
  limitRole(
    auth.role,
    Left(authErrorMsg(Some(auth.role))).pure[IO]
  ):
    traceMappable("listDocuments"):
      println("Listing docs... ")
      db.listDocuments(auth.orgId, auth.role, start, end)

def deleteDocument(auth: AuthData)(id: String)(using
    db: DocDB[IO]
): IO[Either[String, Unit]] =
  limitRole(
    auth.role,
    Left(authErrorMsg(Some(auth.role))).pure[IO]
  ):
    traceMappable("deleteDocument"):
      println("Deleting doc... ")
      db.deleteDocument(UUID.fromString(id), auth.orgId, auth.role)

def createDoc(auth: AuthData)(
    payload: DocumentCreationPayload
)(implicit db: DocDB[IO]): IO[Either[String, String]] =
  limitRole(
    auth.role,
    Left(authErrorMsg(Some(auth.role))).pure[IO]
  ):
    traceMappable("createDoc"):
      println("Creating doc... ")
      val document =
        payload.toDocument(UUID.randomUUID(), payload.content.split(" ").length)
      getEmbeddingAPI("gridoai-ml")
        .embed(document.content)
        .flatMapRight(vec =>
          db.addDocument(
            DocumentWithEmbedding(document, vec),
            auth.orgId,
            auth.role
          )
        )

def ask(auth: AuthData)(messages: List[Message])(implicit
    db: DocDB[IO]
): IO[Either[String, String]] = traceMappable("ask"):
  messages.last.from match
    case MessageFrom.Bot =>
      IO.pure(Left("Last message should be from the user"))
    case MessageFrom.User =>
      val llm = getLLM("palm2")
      println("Using llm: " + llm.toString())
      val prompt = llm.mergeMessages(messages)
      prompt
        .flatMapRight(searchDoc(auth))
        .flatMapRight(docs => prompt.flatMapRight(llm.ask(docs)))
