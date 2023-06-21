package com.gridoai.adapters

import munit.FunSuite

import java.nio.file.Files
import java.nio.file.Paths
import scala.util.Failure
import scala.util.Success

class PptxParserTest extends FunSuite {

  test("parsePptx extracts text from pptx file") {
    // Replace with the path to your test pptx file
    val testFilePath =
      "./src/test/resources/presentation.pptx"
    val content = Files.readAllBytes(Paths.get(testFilePath))

    val result = parsePptx(content)

    result match {
      case Success(text) =>
        // Replace with the expected text from your test pptx file
        val expectedText = """GridoAI
This is for testing
Testing presentation
Introduction
Why?
Its fast
Its nice
Its cheap"""
        assertEquals(text, expectedText)
      case Failure(exception) =>
        fail(s"Failed to parse pptx file: ${exception.getMessage}")
    }
  }
}
