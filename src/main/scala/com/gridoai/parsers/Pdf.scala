package com.gridoai.adapters

import cats.effect.IO
import cats.effect.Sync
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import com.gridoai.parsers.{ExtractTextError, FileFormat}
trait PdfParser[F[_]]:
  def load(bytes: Array[Byte]): F[PDDocument]
  def getText(doc: PDDocument): F[String]
  def close(doc: PDDocument): F[Unit]

object PdfBoxParser extends PdfParser[IO]:
  def load(bytes: Array[Byte]) =
    Sync[IO].delay(PDDocument.load(bytes))

  def getText(doc: PDDocument) =
    Sync[IO].delay {
      val stripper = new PDFTextStripper()
      stripper.setSortByPosition(true)
      stripper.getText(doc)
    }

  def close(doc: PDDocument) =
    Sync[IO].delay(doc.close())

def extractTextFromPdf(
    content: Array[Byte]
): IO[Either[ExtractTextError, String]] = PdfBoxParser
  .load(content)
  .flatMap(doc => PdfBoxParser.getText(doc).map(text => (doc, text)))
  .flatMap((doc, text) => PdfBoxParser.close(doc).map(_ => text))
  .attempt
  .map(_.left.map(t => ExtractTextError(FileFormat.PDF, t.getMessage)))
