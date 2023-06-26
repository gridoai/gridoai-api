package com.gridoai.adapters

import munit.FunSuite

import java.nio.file.Files
import java.nio.file.Paths

class DocxParserTest extends FunSuite {

  test("extractTextFromDocx extracts text from Docx file") {
    // Replace with the path to your test Docx file
    val testFilePath =
      "./src/test/resources/test.docx"
    val expectedContentPath = "./src/test/resources/expected_docx.txt"
    val content = Files.readAllBytes(Paths.get(testFilePath))
    val expectedContent = Files.readString(Paths.get(expectedContentPath))

    val result = extractTextFromDocx(content)

    result match {

      case Right(text) =>
        println(text)

        assertEquals(
          text,
          expectedContent
        )
      case Left(exception) =>
        fail(s"Failed to parse Docx file: ${exception.message}")
    }
  }
}
