package com.gridoai.services.doc

import munit.FunSuite

import java.nio.file.Files
import java.nio.file.Paths

class ChunkingTest extends FunSuite {

  test("chunkContent splits the content of a document") {
    // Replace with the path to your test Docx file
    val testFilePath =
      "./src/test/resources/notchunked.txt"
    val expectedContentPath = "./src/test/resources/expected_chunks.txt"
    val content = Files.readString(Paths.get(testFilePath))
    val expectedContent = Files.readString(Paths.get(expectedContentPath))

    val result = chunkContent(content)

    result match {

      case Right(text) =>
        println(text)

        assertEquals(
          text,
          expectedContent
        )
      case Left(exception) =>
        fail(s"Failed to read file: ${exception.message}")
    }
  }
}
