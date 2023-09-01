package com.gridoai.services.doc

import java.util.UUID
import cats.effect.IO
import cats.implicits._

import com.gridoai.domain.*
import com.gridoai.utils.*
import com.gridoai.adapters.embeddingApi.*
import com.gridoai.models.DocDB

def calculateChunkStarts(
    wordQuantity: Int,
    chunkSize: Int,
    overlapSize: Int
): List[Int] =
  println(s"wordQuantity: $wordQuantity")
  println(s"chunkSize: $chunkSize")
  println(s"overlapSize: $overlapSize")
  List
    .range(
      0,
      (wordQuantity - overlapSize).max(0) + 1,
      chunkSize - overlapSize
    )
    .traceFn: output =>
      s"output: $output"

def chunkContent(
    content: String,
    chunkSize: Int,
    overlapSize: Int
): List[(String, Int, Int)] =
  val words = content.split(" ")
  calculateChunkStarts(words.length, chunkSize, overlapSize)
    .map(i => (words.slice(i, i + chunkSize).mkString(" "), i, i + chunkSize))

def makeChunks(document: Document): List[Chunk] =
  chunkContent(document.content, 200, 100)
    .map((content, startPos, endPos) =>
      Chunk(
        documentUid = document.uid,
        documentName = document.name,
        documentSource = document.source,
        uid = UUID.randomUUID(),
        content = content,
        tokenQuantity = content.filter(_ != ' ').length / 4,
        startPos = startPos,
        endPos = endPos
      )
    )
    .traceFn: chunks =>
      val uids = chunks.map(_.uid).mkString(", ")
      s"generated chunks: $uids"

def getChunks(
    calculateChunkTokenQuantity: Chunk => Int,
    tokenLimit: Int,
    scope: Option[List[UID]],
    orgId: String,
    role: String,
    pageSize: Int = 100
)(
    vec: Embedding
)(implicit db: DocDB[IO]): IO[Either[String, List[SimilarChunk]]] =

  def getChunksRecursively(
      offset: Int,
      selectedChunks: List[SimilarChunk],
      totalTokens: Int
  ): IO[Either[String, List[SimilarChunk]]] =
    if (totalTokens == tokenLimit) selectedChunks.asRight.pure[IO]
    else if (totalTokens > tokenLimit)
      filterExcessTokens(
        selectedChunks,
        calculateChunkTokenQuantity,
        tokenLimit
      ).asRight.pure[IO]
    else
      db.getNearChunks(vec, scope, offset, pageSize, orgId, role)
        .flatMapRight:
          case Nil =>
            IO.pure(Right(selectedChunks))
          case chunks =>
            println(
              s"getChunks: offset:$offset pageSize:$pageSize totalTokens:$totalTokens tokenLimit:$tokenLimit"
            )
            val newSelectedChunks = mergeNewChunksToList(selectedChunks, chunks)
            val newTotalTokens = newSelectedChunks
              .map(_.chunk)
              .map(calculateChunkTokenQuantity)
              .sum
            getChunksRecursively(
              offset + pageSize,
              newSelectedChunks,
              newTotalTokens
            )

  getChunksRecursively(0, List.empty, 0)

/** This function filters out excess tokens from a list of chunks, starting from
  * the end of the list.
  *
  * @param chunks
  *   A list of chunks from which excess tokens need to be removed.
  * @param calculateChunkTokenQuantity
  *   A function that calculates the number of tokens in a chunk.
  * @param tokenLimit
  *   The maximum number of tokens allowed in the final list of chunks.
  * @return
  *   A list of chunks where the total number of tokens does not exceed the
  *   token limit. Excess tokens are removed starting from the end of the input
  *   list.
  */
def filterExcessTokens(
    chunks: List[SimilarChunk],
    calculateChunkTokenQuantity: Chunk => Int,
    tokenLimit: Int
): List[SimilarChunk] =
  @annotation.tailrec
  def loop(
      remainingChunks: List[SimilarChunk],
      totalTokens: Int,
      selectedChunks: List[SimilarChunk]
  ): List[SimilarChunk] =
    remainingChunks match
      case currentChunk :: newRemainingChunks =>
        val newSelectedChunks =
          mergeNewChunkToList(selectedChunks, currentChunk)
        val newTotalTokens = newSelectedChunks
          .map(_.chunk)
          .map(calculateChunkTokenQuantity)
          .sum
        if newTotalTokens > tokenLimit then selectedChunks
        else
          loop(
            newRemainingChunks,
            newTotalTokens,
            newSelectedChunks
          )
      case Nil => selectedChunks

  loop(chunks, 0, List.empty)

def mergeNewChunksToList(
    chunks: List[SimilarChunk],
    newChunks: List[SimilarChunk]
): List[SimilarChunk] =
  if newChunks.length > 0 then
    val (newChunk :: remainingChunks) = newChunks
    mergeNewChunksToList(mergeNewChunkToList(chunks, newChunk), remainingChunks)
  else chunks

def mergeNewChunkToList(
    chunks: List[SimilarChunk],
    newChunk: SimilarChunk
): List[SimilarChunk] =

  val (nearChunks, notNearChunks) = chunks.partition: c =>
    c.chunk.documentUid == newChunk.chunk.documentUid
      && c.chunk.startPos < newChunk.chunk.endPos
      && c.chunk.endPos > newChunk.chunk.startPos

  (newChunk :: nearChunks).sortBy(_.chunk.startPos) match
    case List(singleChunk) => chunks :+ newChunk
    case chunksToMerge @ List(firstChunk, secondChunk) =>
      SimilarChunk(
        chunk = mergeTwoChunks(firstChunk.chunk, secondChunk.chunk),
        distance = chunksToMerge.map(_.distance).min
      ) :: notNearChunks
    case chunksToMerge @ List(firstChunk, secondChunk, thirdChunk) =>
      SimilarChunk(
        chunk = mergeTwoChunks(
          mergeTwoChunks(firstChunk.chunk, secondChunk.chunk),
          thirdChunk.chunk
        ),
        distance = chunksToMerge.map(_.distance).min
      ) :: notNearChunks

def mergeTwoChunks(firstChunk: Chunk, secondChunk: Chunk): Chunk =
  val allWords = firstChunk.chunk.content
    .split(" ")
    .slice(
      0,
      secondChunk.chunk.startPos - firstChunk.chunk.startPos
    ) ++ secondChunk.chunk.content.split(" ")
  Chunk(
    documentUid = firstChunk.chunk.documentUid,
    documentName = firstChunk.chunk.documentName,
    documentSource = firstChunk.chunk.documentSource,
    uid = UUID.randomUUID(),
    content = allWords.mkString(" "),
    tokenQuantity = 0,
    startPos = firstChunk.chunk.startPos,
    endPos = secondChunk.chunk.endPos
  )
