package com.gridoai.services.doc

import munit.FunSuite

import java.nio.file.Files
import java.nio.file.Paths

class ChunkingTest extends FunSuite {

  test("chunkContent splits the content of a document") {
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

  test("mergeNewChunk smart merges a new chunk") {
    val docUid = UUID.randomUUID()
    val differentUid = UUID.randomUUID()
    val oldChunks = List(
      Chunk(
        documentUid = docUid,
        documentName = docUid,
        documentSource = Source.Upload,
        uid = UUID.randomUUID(),
        content = "2 3 4 5",
        tokenQuantity = 0,
        startPos = 2,
        endPos = 6
      ),
      Chunk(
        documentUid = docUid,
        documentName = docUid,
        documentSource = Source.Upload,
        uid = UUID.randomUUID(),
        content = "8 9 10 11",
        tokenQuantity = 0,
        startPos = 8,
        endPos = 12
      ),
      Chunk(
        documentUid = differentUid,
        documentName = differentUid,
        documentSource = Source.Upload,
        uid = UUID.randomUUID(),
        content = "bla bla bla",
        tokenQuantity = 0,
        startPos = 8,
        endPos = 11
      )
    )
    val newChunk = Chunk(
      documentUid = docUid,
      documentName = docUid,
      documentSource = Source.Upload,
      uid = UUID.randomUUID(),
      content = "4 5 6 7 8 9 10",
      tokenQuantity = 0,
      startPos = 4,
      endPos = 11
    )
    val mergedChunks = mergeNewChunk(oldChunks, newChunk)

    val expectedChunks = List(
      Chunk(
        documentUid = docUid,
        documentName = docUid,
        documentSource = Source.Upload,
        uid = UUID.randomUUID(),
        content = "2 3 4 5 6 7 8 9 10 11",
        tokenQuantity = 0,
        startPos = 2,
        endPos = 12
      ),
      Chunk(
        documentUid = differentUid,
        documentName = differentUid,
        documentSource = Source.Upload,
        uid = UUID.randomUUID(),
        content = "bla bla bla",
        tokenQuantity = 0,
        startPos = 8,
        endPos = 11
      )
    )
    assertEquals(
      mergedChunks,
      expectedChunks
    )
  }
}
