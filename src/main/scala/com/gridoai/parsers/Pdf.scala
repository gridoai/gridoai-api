package com.gridoai.adapters

import cats.effect.IO
import cats.effect.Sync
import cats.data.EitherT
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper

import com.gridoai.parsers.ExtractTextError
import com.gridoai.parsers.FileFormat
import com.gridoai.utils._

trait PdfParser[F[_]]:
  def load(bytes: Array[Byte]): F[PDDocument]
  def getText(doc: PDDocument): F[String]
  def close(doc: PDDocument): F[Unit]

object PdfBoxParser extends PdfParser[IO]:
  val stripper = new PDFTextStripper()
  stripper.setSortByPosition(true)

  def load(bytes: Array[Byte]) =
    Sync[IO].delay(PDDocument.load(bytes))

  def getText(doc: PDDocument) =
    Sync[IO].delay(stripper.getText(doc))

  def close(doc: PDDocument) =
    Sync[IO].delay(doc.close())

def extractTextFromPdf(
    content: Array[Byte]
): EitherT[IO, ExtractTextError, String] = PdfBoxParser
  .load(content)
  .flatMap(doc => PdfBoxParser.getText(doc).map(text => (doc, text)))
  .flatMap((doc, text) => PdfBoxParser.close(doc).map(_ => text))
  .attempt
  .map(_.left.map(t => ExtractTextError(FileFormat.PDF, t.getMessage)))
  .asEitherT
