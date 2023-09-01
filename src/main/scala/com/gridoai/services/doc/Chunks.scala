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
): List[String] =
  val words = content.split(" ")
  calculateChunkStarts(words.length, chunkSize, overlapSize)
    .map(i => words.slice(i, i + chunkSize).mkString(" "))

def makeChunks(document: Document): List[Chunk] =
  chunkContent(document.content, 200, 100)
    .map(content =>
      Chunk(
        documentUid = document.uid,
        documentName = document.name,
        documentSource = document.source,
        uid = UUID.randomUUID(),
        content = content,
        tokenQuantity = content.filter(_ != ' ').length / 4
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
            val newSelectedChunks = selectedChunks ++ chunks
            val newTotalTokens = mergeChunks(newSelectedChunks)
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
        val newSelectedChunks = mergeChunks(selectedChunks :+ currentChunk)
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

def mergeChunks(chunks: List[SimilarChunk]): List[SimilarChunk] =
  chunks
