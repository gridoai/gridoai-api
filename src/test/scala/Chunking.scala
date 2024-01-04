package com.gridoai.services.doc

import munit.FunSuite

import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import com.gridoai.domain.*

class ChunkingTest extends FunSuite {

  test("chunkContent splits the content of a document") {
    val testFilePath =
      "./src/test/resources/notchunked.txt"
    val expectedContentPath = "./src/test/resources/expected_chunks.txt"
    val content = Files.readString(Paths.get(testFilePath))
    val expectedContent = Files.readString(Paths.get(expectedContentPath))

    val text = chunkContent(content, 200, 100).map(_._1)

    assertEquals(
      text.mkString("\n$$$$$$$$\n"),
      expectedContent
    )
  }

  test("chunkContent splits small contents") {
    val content = "The sky is blue"
    val chunks = chunkContent(content, 500, 100).map(_._1)
    val expectedChunks = List("The sky is blue")
    assertEquals(
      chunks,
      expectedChunks
    )
  }

  test("mergeNewChunk smart merges a new chunk") {
    val docUid = UUID.randomUUID()
    val differentUid = UUID.randomUUID()
    val uidForComparison = UUID.randomUUID()
    val oldChunks = List(
      RelevantChunk(
        chunk = Chunk(
          documentUid = docUid,
          documentName = docUid.toString,
          documentSource = Source.Upload,
          uid = UUID.randomUUID(),
          content = "2 3 4 5",
          tokenQuantity = 0,
          startPos = 2,
          endPos = 6
        ),
        relevance = 1
      ),
      RelevantChunk(
        chunk = Chunk(
          documentUid = docUid,
          documentName = docUid.toString,
          documentSource = Source.Upload,
          uid = UUID.randomUUID(),
          content = "8 9 10 11",
          tokenQuantity = 0,
          startPos = 8,
          endPos = 12
        ),
        relevance = 0.5
      ),
      RelevantChunk(
        chunk = Chunk(
          documentUid = differentUid,
          documentName = differentUid.toString,
          documentSource = Source.Upload,
          uid = differentUid,
          content = "bla bla bla",
          tokenQuantity = 0,
          startPos = 8,
          endPos = 11
        ),
        relevance = 0.3
      )
    )
    val newChunk = RelevantChunk(
      chunk = Chunk(
        documentUid = docUid,
        documentName = docUid.toString,
        documentSource = Source.Upload,
        uid = UUID.randomUUID(),
        content = "4 5 6 7 8 9 10",
        tokenQuantity = 0,
        startPos = 4,
        endPos = 11
      ),
      relevance = 100
    )
    val mergedChunks = mergeNewChunkToList(oldChunks, newChunk).map: c =>
      c.copy(chunk = c.chunk.copy(uid = uidForComparison))

    val expectedChunks = List(
      RelevantChunk(
        chunk = Chunk(
          documentUid = docUid,
          documentName = docUid.toString,
          documentSource = Source.Upload,
          uid = uidForComparison,
          content = "2 3 4 5 6 7 8 9 10 11",
          tokenQuantity = 0,
          startPos = 2,
          endPos = 12
        ),
        relevance = 100
      ),
      RelevantChunk(
        chunk = Chunk(
          documentUid = differentUid,
          documentName = differentUid.toString,
          documentSource = Source.Upload,
          uid = uidForComparison,
          content = "bla bla bla",
          tokenQuantity = 0,
          startPos = 8,
          endPos = 11
        ),
        relevance = 0.3
      )
    )
    assertEquals(
      mergedChunks,
      expectedChunks
    )
  }
}
