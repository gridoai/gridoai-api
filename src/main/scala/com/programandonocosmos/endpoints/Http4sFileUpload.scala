package com.programandonocosmos.endpoints

import cats.effect.*
import cats.implicits.*
import com.programandonocosmos.domain.Document
import com.programandonocosmos.models.DocDB
import com.programandonocosmos.services.doc.createDoc
import org.apache.pdfbox.pdmodel.*
import org.apache.pdfbox.text.PDFTextStripper
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.multipart.*

import java.io.File
import java.nio.file.Paths
import java.util.UUID
import fs2.io.file.{Files, Path}

def fileUploadEndpoint(implicit db: DocDB[IO]) = HttpRoutes.of[IO] {
  case req @ POST -> Root / "upload" / filename =>
    req.decode[Multipart[IO]] { m =>
      m.parts
        .traverse_(
          _.body
            .through(Files[IO].writeAll(Path("temp.pdf")))
            .compile
            .drain
        )
        .flatMap { _ =>
          val doc = PDDocument.load(File("temp.pdf"))
          val stripper = PDFTextStripper()
          val text = stripper.getText(doc)
          doc.close()
          println("loading doc...")

          createDoc(
            Document(
              UUID.randomUUID(),
              filename,
              text,
              f"$filename.pdf",
              text.length
            )
          ).attempt.flatMap {
            case Right(_) => Ok("Doc created!")
            case Left(e)  => BadRequest(e.getMessage)
          }
        }
    }
}
