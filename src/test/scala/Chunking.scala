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

    val text = chunkContent(content, 200, 100)

    assertEquals(
      text.mkString("\n$$$$$$$$\n"),
      expectedContent
    )
  }

  test("chunkContent splits small contents") {
    val content = "The sky is blue"
    val chunks = chunkContent(content, 500, 100)
    val expectedChunks = List("The sky is blue")
    assertEquals(
      chunks,
      expectedChunks
    )
  }
}
