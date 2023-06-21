package com.gridoai.adapters

import com.gridoai.utils.trace
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTextShape
import sttp.model.Part

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import scala.util.Try

import collection.JavaConverters.asScalaBufferConverter

def parsePptx(content: Array[Byte]): Try[String] = Try:
  println("Parsing pptx")
  XMLSlideShow(
    ByteArrayInputStream(content)
  ).getSlides.asScala.toList
    .flatMap(_.getShapes.asScala.collect:
      case textShape: XSLFTextShape => textShape.getText
    )
    .mkString("\n")
