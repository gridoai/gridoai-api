package com.gridoai.adapters

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape

import java.io.ByteArrayInputStream

import scala.jdk.CollectionConverters.*
import scala.util.Try
import com.gridoai.parsers.{ExtractTextError, FileFormats}

def extractTextFromPptx(
    content: Array[Byte]
): Either[ExtractTextError, String] = Try {
  println("Parsing pptx")
  XMLSlideShow(
    ByteArrayInputStream(content)
  ).getSlides.asScala.toList
    .flatMap(_.getShapes.asScala.collect:
      case textShape: XSLFTextShape => textShape.getText
    )
    .mkString("\n")
}.toEither.left
  .map(e => ExtractTextError(FileFormats.PPTX, e.getMessage))