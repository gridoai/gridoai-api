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

import com.gridoai.auth.limitRole
import com.gridoai.auth.authErrorMsg
import com.gridoai.auth.AuthData

def searchDoc(auth: AuthData)(text: String)(using
    db: DocDB[IO]
): IO[Either[String, List[Document]]] =
  println(s"Searching for: $text")

  getEmbeddingAPI("gridoai-ml")
    .embed(text)
    .flatMapRight(vec => db.getNearDocuments(vec, 5, auth.orgId, auth.role))
    .traceRight(docs =>
      s"result documents: ${docs.map(doc => s"${doc.document.name} (${doc.distance})").mkString(", ")}"
    )
    .mapRight(_.map(_.document))

def mapExtractToUploadError(e: ExtractTextError): FileUploadError =
  FileParseError(e.format, e.message)

def extractText(
    name: String,
    body: Array[Byte]
): IO[Either[ExtractTextError, String]] = traceMappable("extractText"):
  println("file name: " + name)

  FileFormat.ofFilename(name) match
    case Some(FileFormat.PDF) =>
      extractTextFromPdf(body)
    case Some(
          FileFormat.DOCX
        ) =>
      extractTextFromDocx(body).pure[IO]
    case Some(
          FileFormat.PPTX
        ) =>
      extractTextFromPptx(body).pure[IO]
    case Some(FileFormat.Plaintext) => IO.pure(Right(String(body)))
    case Some(other) =>
      Left(
        ExtractTextError(
          other,
          "Unknown file format"
        )
      ).pure[IO]
    case None =>
      IO.pure(
        Left(
          ExtractTextError(
            FileFormat.Unknown(name),
            "Unknown file format"
          )
        )
      )

def uploadFile(
    auth: AuthData
)(name: String, body: Array[Byte])(using
    db: DocDB[IO]
) =
  println(s"Uploading document... ${name}")

  extractText(name, body)
    .map(_.left.map(mapExtractToUploadError))
    .map(extracted =>
      println(
        s"Extracted[${name}]: ${extracted.map(_.slice(0, 20))} "
      )
      (name, extracted)
    )
    .flatMap:
      case (filename, Right(content)) =>
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
    .attempt
    .flatMap:
      case Left(e) =>
        println(s"Error uploading file: $e")
        IO.pure(Left(UnknownError(e.getMessage)))
      case Right(x) => IO.pure(x)

type FileUpErr = List[Either[FileUploadError, String]]
type FileUpOutput = List[String]

def uploadDocuments(auth: AuthData)(source: FileUpload)(using
    db: DocDB[IO]
): IO[Either[FileUpErr, FileUpOutput]] =
  limitRole(
    auth.role,
    (Left(List(Left(UnauthorizedError(authErrorMsg(Some(auth.role))))))
      .pure[IO])
  ):
    println(s"Uploading files... ${source.files.length}")
    source.files
      .traverse: f =>
        uploadFile(auth)(
          f.fileName.getOrElse("file"),
          Files.readAllBytes(f.body.toPath())
        )
      .map: eithers =>
        val (errors, successes) = eithers.partitionMap(identity)
        if (errors.nonEmpty) Left(eithers)
        else Right(successes)

def listDocuments(auth: AuthData)(
    start: Int,
    end: Int
)(using db: DocDB[IO]): IO[Either[String, PaginatedResponse[List[Document]]]] =
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

def splitContent(content: String): List[String] =
  List(content)

def createDoc(auth: AuthData)(
    payload: DocumentCreationPayload
)(implicit db: DocDB[IO]): IO[Either[String, String]] =
  limitRole(
    auth.role,
    Left(authErrorMsg(Some(auth.role))).pure[IO]
  ):
    traceMappable("createDoc"):
      val embedding = getEmbeddingAPI("gridoai-ml")
      println("Creating doc... ")
      splitContent(payload.content)
        .map(content =>
          Document(
            uid = UUID.randomUUID(),
            name = payload.name,
            source = payload.source,
            content = content,
            tokenQuantity = content.split(" ").length
          )
        )
        .traverse(document =>
          embedding
            .embed(document.content)
            .flatMapRight(vec =>
              db.addDocument(DocumentWithEmbedding(document, vec), auth.orgId, auth.role)
            )
        )
        .map(collectLeftsOrElseUnit)
        .mapLeft(x => x.mkString(","))

def ask(auth: AuthData)(messages: List[Message])(implicit
    db: DocDB[IO]
): IO[Either[String, String]] = traceMappable("ask"):
  messages.last.from match
    case MessageFrom.Bot =>
      IO.pure(Left("Last message should be from the user"))
    case MessageFrom.User =>
      val llm = getLLM("palm2")
      println("Used llm: " + llm.toString())

      llm
        .mergeMessages(messages)
        .trace("prompt built by llm")
        .flatMapRight(searchDoc(auth))
        .flatMapRight(docs =>
          val answer = llm.ask(docs)(messages)
          val sources = docs.map(_.source).mkString(", ")
          if docs.length < 1 then answer
          else answer.mapRight(x => s"$x\nsources: $sources")
        )
