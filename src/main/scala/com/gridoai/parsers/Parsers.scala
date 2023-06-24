package com.gridoai.parsers

import scala.util.Try
import com.gridoai.endpoints.FileUploadError

enum FileFormats:
  case PDF, PPTX, DOCX

case class ExtractTextError(format: FileFormats, message: String)
