package com.programandonocosmos.endpoints

import cats.effect.*
import cats.implicits.*
import com.programandonocosmos.adapters.PdfBoxParser
import com.programandonocosmos.adapters.PdfParser
import com.programandonocosmos.domain.Document
import com.programandonocosmos.models.DocDB
import com.programandonocosmos.services.doc.createDoc
import fs2.io.file.Files
import fs2.io.file.Path
import org.apache.pdfbox.pdmodel.*
import org.apache.pdfbox.text.PDFTextStripper
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.multipart.*

import java.io.File
import java.nio.file.Paths
import java.util.UUID
import scala.util.Try

enum FileUploadError:
  case PdfParsingError(m: String)
  case DocumentCreationError(m: String)
  case UnknownError(m: String)

import FileUploadError._

def extractBytes(m: Multipart[IO]): IO[Array[Byte]] =
  m.parts
    .traverse(_.body.compile.to(Array))
    .map(_.flatten.toArray)

def loadAndExtractText(
    byteArray: Array[Byte]
): IO[Either[PdfParsingError, String]] =
  PdfBoxParser
    .load(byteArray)
    .flatMap(doc => PdfBoxParser.getText(doc).map(text => (doc, text)))
    .flatMap((doc, text) => PdfBoxParser.close(doc).map(_ => text))
    .attempt
    .map(_.left.map(t => PdfParsingError(t.getMessage)))

def createAndStoreDocument(filename: String, text: String)(implicit
    db: DocDB[IO]
): IO[Either[DocumentCreationError, Unit]] =
  createDoc(
    Document(
      UUID.randomUUID(),
      filename,
      text,
      filename,
      text.length
    )
  ).map:
    case Right(_) => Right(())
    case Left(e)  => Left(DocumentCreationError(e))

def mapToResponse(result: Either[FileUploadError, Unit]): IO[Response[IO]] =
  result match
    case Right(_) => Ok("Doc created!")
    case Left(PdfParsingError(e)) =>
      println("Error parsing PDF file: " + e)
      BadRequest("An error occurred while parsing the PDF file.")
    case Left(DocumentCreationError(e)) =>
      println("Error creating the document: " + e)
      BadRequest("An error occurred while creating the document.")
    case Left(e) =>
      println("Unknown error: " + e)
      InternalServerError(
        "An unknown error occurred while processing the request."
      )

def fileUploadEndpoint(implicit db: DocDB[IO]) =
  HttpRoutes.of[IO]:
    case req @ POST -> Root / "upload" / filename =>
      req.decode[Multipart[IO]]: m =>
        extractBytes(m)
          .flatMap(loadAndExtractText)
          .flatMap:
            case Right(text) => createAndStoreDocument(filename, text)
            case Left(error) => IO.pure(Left(error))
          .flatMap(mapToResponse)