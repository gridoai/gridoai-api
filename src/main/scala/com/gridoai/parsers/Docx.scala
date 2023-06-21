package com.gridoai.adapters

import com.gridoai.utils.trace
import com.gridoai.utils.|>
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import sttp.model.Part

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import scala.io.Source
import scala.util.Try

def parseDocx(content: Array[Byte]): Try[String] = Try:
  import java.io.ByteArrayInputStream
  ByteArrayInputStream(content)
    |> (XWPFDocument(_))
    |> (XWPFWordExtractor(_))
    |> (_.getText.trace)
