package com.programandonocosmos.adapters

import cats.effect.IO
import munit.FunSuite

import java.nio.file.Files
import java.nio.file.Paths
import scala.util.Failure
import scala.util.Success

class DocxParserTest extends FunSuite {

  test("parseDocx extracts text from Docx file") {
    // Replace with the path to your test Docx file
    val testFilePath =
      "./src/test/resources/test.docx"
    val expectedContentPath = "./src/test/resources/expected_docx.txt"
    val content = Files.readAllBytes(Paths.get(testFilePath))
    val expectedContent = Files.readString(Paths.get(expectedContentPath))

    val result = parseDocx(content)

    result match {

      case Success(text) =>
        println(text)

        assertEquals(
          text,
          expectedContent
        )
      case Failure(exception) =>
        fail(s"Failed to parse Docx file: ${exception.getMessage}")
    }
  }
}
