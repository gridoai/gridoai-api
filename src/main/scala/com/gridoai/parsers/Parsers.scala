package com.gridoai.parsers

enum FileFormats:
  case PDF, PPTX, DOCX
  case Unknown(ext: String = "")

case class ExtractTextError(format: FileFormats, message: String)
