package com.programandonocosmos.adapters

import cats.effect.IO
import cats.effect.Sync
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper

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
      stripper.getText(doc)
    }

  def close(doc: PDDocument) =
    Sync[IO].delay(doc.close())