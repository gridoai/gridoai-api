package com.gridoai.parsers

enum FileFormat:
  case PDF, PPTX, DOCX, Plaintext, HTML
  case Unknown(ext: String = "")

import FileFormat._
case class ExtractTextError(format: FileFormat, message: String)

object FileFormat:
  def ofExtension(ext: String) =
    ext match
      case "pdf"                => PDF
      case "docx"               => DOCX
      case "pptx"               => PPTX
      case "txt" | "md" | "mdx" => Plaintext
      case "html"               => HTML
      case other                => Unknown(other)

  def ofFilename(filename: String) =
    filename.split("\\.").lastOption.map(ofExtension)
  def fromString(s: String) =
    s match
      case "application/pdf" => PDF
      case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" =>
        DOCX
      case "application/vnd.openxmlformats-officedocument.presentationml.presentation" =>
        PPTX
      case "text/plain" | "text/markdown" | "text/x-markdown" => Plaintext
      case "text/html"                                        => HTML
      case other                                              => Unknown(other)
