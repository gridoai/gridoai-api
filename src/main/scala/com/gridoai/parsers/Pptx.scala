package com.gridoai.adapters

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape

import java.io.ByteArrayInputStream

import scala.jdk.CollectionConverters.*
import scala.util.Try
import com.gridoai.parsers.{ExtractTextError, FileFormat}

def extractTextFromPptx(
    content: Array[Byte]
): Either[ExtractTextError, String] = Try {
  XMLSlideShow(
    ByteArrayInputStream(content)
  ).getSlides.asScala.toList
    .flatMap(_.getShapes.asScala.collect:
      case textShape: XSLFTextShape => textShape.getText
    )
    .mkString("\n")
}.toEither.left
  .map(e => ExtractTextError(FileFormat.PPTX, e.getMessage))
