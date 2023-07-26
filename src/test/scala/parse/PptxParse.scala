package com.gridoai.adapters

import munit.FunSuite

import java.nio.file.Files
import java.nio.file.Paths

class PptxParserTest extends FunSuite {

  test("extractTextFromPptx extracts text from pptx file") {
    // Replace with the path to your test pptx file
    val testFilePath =
      "./src/test/resources/presentation.pptx"
    val content = Files.readAllBytes(Paths.get(testFilePath))

    val result = extractTextFromPptx(content)

    result match {
      case Right(text) =>
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
      case Left(error) =>
        fail(s"Failed to parse pptx file: ${error.message}")
    }
  }
}
