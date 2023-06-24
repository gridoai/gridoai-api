package com.gridoai.adapters

import com.gridoai.utils.trace
import com.gridoai.utils.|>
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import scala.util.Try
import com.gridoai.parsers.ExtractTextError
import com.gridoai.parsers.FileFormats
import java.io.ByteArrayInputStream

def extractTextFromDocx(
    content: Array[Byte]
): Either[ExtractTextError, String] = Try {
  ByteArrayInputStream(content)
    |> (XWPFDocument(_))
    |> (XWPFWordExtractor(_))
    |> (_.getText.trace)
}.toEither.left.map(e => ExtractTextError(FileFormats.DOCX, e.getMessage))
