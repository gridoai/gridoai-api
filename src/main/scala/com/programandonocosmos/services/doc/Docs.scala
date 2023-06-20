package com.programandonocosmos.services.doc

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import cats.implicits.catsSyntaxApplicativeId
import cats.syntax.parallel.*
import com.programandonocosmos.adapters.PdfBoxParser
import com.programandonocosmos.adapters.contextHandler.DocumentApiClient
import com.programandonocosmos.adapters.contextHandler.MessageResponse
import com.programandonocosmos.domain._
import com.programandonocosmos.endpoints.FileUpload
import com.programandonocosmos.models.DocDB
import com.programandonocosmos.utils.trace

import java.util.UUID

import util.chaining.scalaUtilChainingOps
def searchDoc(x: String)(using
    db: DocDB[IO]
): IO[Either[String, List[Document]]] =
  println(s"Searching for: $x")
  DocumentApiClient.neardocs(x).map { response =>
    response
      .fold(
        error =>
          (Left(error match
            case df: io.circe.Error => s"Parsing error: ${df.getMessage}"
            case other              => other.toString
          )),
        response =>
          (Right(
            response.message.map(i =>
              Document(
                UUID.fromString(i.uid),
                i.path.getOrElse(""),
                i.content,
                "",
                0
              )
            )
          ))
      )
  }
import com.programandonocosmos.endpoints._

import FileUploadError._
import java.nio.file.Files
def extractText(
    file: sttp.model.Part[java.io.File]
): IO[Either[FileUploadError, String]] =
  import com.programandonocosmos.adapters.*
  val body = Files.readAllBytes(file.body.toPath)
  val name = file.fileName
  println("file name: " + name)
  println("file content type: " + file.contentType)
  file.contentType match
    case Some("application/pdf") =>
      parsePdf(body).attempt
        .map(_.left.map(t => FileParseError(FileFormats.PDF, t.getMessage)))
    case Some(
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ) =>
      parseDocx(body).toEither.left
        .map(e => FileParseError(FileFormats.DOCX, e.getMessage))
        .pure[IO]
    case Some(
          "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        ) =>
      parsePptx(body).toEither.left
        .map(e => FileParseError(FileFormats.PPTX, e.getMessage))
        .pure[IO]
    case Some("text/plain") => IO.pure(Right(String(body)))
    case Some(otherFormat) =>
      IO.pure(Left(UnknownError(s"Unknown file format ${otherFormat}")))
    case None => IO.pure(Left(UnknownError("Unknown file format")))

def uploadDocuments(
    source: FileUpload
)(using db: DocDB[IO]): IO[List[Either[FileUploadError, Unit]]] =
  source.files.traverse: file =>
    println(s"Uploading document... $file")

    extractText(file)
      .map(extracted =>
        println(s"Extracted[${file.fileName}]: $extracted ")
        (file.fileName, extracted)
      )
      .flatMap:
        case (Some(filename), Right(content)) =>
          createDoc(DocCreationPayload(filename, content))
            .map(_.leftMap(DocumentCreationError.apply))

        case (_, Left(e: FileUploadError)) =>
          IO.pure(Left(e))
        case (None, _) =>
          IO.pure(Left(UnknownError("File extension not known")))

def createDoc(
    docInput: DocCreationPayload
)(implicit db: DocDB[IO]): IO[Either[String, Unit]] = {
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
  ).parTupled.map {
    case (Right(()), Right(_)) => Right(())
    case (_, Left(e))          => Left(e.toString)
  }
}
