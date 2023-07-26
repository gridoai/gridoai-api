package com.gridoai.services.doc

import java.util.UUID
import cats.effect.IO
import cats.implicits._

import com.gridoai.domain.*
import com.gridoai.utils.*
import com.gridoai.adapters.embeddingApi.*
import com.gridoai.models.DocDB

def chunkContent(
    content: String,
    chunkSize: Int,
    overlapSize: Int
): List[String] =
  val words = content.split(" ")

  List
    .range(
      0,
      words.length + 1 - overlapSize,
      chunkSize - overlapSize
    )
    .map(i => words.slice(i, i + chunkSize).mkString(" "))

def makeChunks(document: Document): List[Chunk] =
  chunkContent(document.content, 500, 100)
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
    orgId: String,
    role: String,
    pageSize: Int = 100
)(
    vec: Embedding
)(implicit db: DocDB[IO]): IO[Either[String, List[SimilarChunk]]] =

  def getChunksRecursively(
      offset: Int,
      acc: List[SimilarChunk],
      totalTokens: Int
  ): IO[Either[String, List[SimilarChunk]]] =
    if (totalTokens == tokenLimit) IO.pure(Right(acc))
    else if (totalTokens > tokenLimit)
      IO.pure(
        Right(filterExcessTokens(acc, calculateChunkTokenQuantity, tokenLimit))
      )
    else if (acc.length < pageSize) IO.pure(Right(acc))
    else
      db.getNearChunks(vec, offset, pageSize, orgId, role)
        .flatMapRight:
          case Nil =>
            IO.pure(Right(acc))
          case chunks =>
            println(
              s"getChunks: offset:$offset pageSize:$pageSize totalTokens:$totalTokens tokenLimit:$tokenLimit"
            )
            val newTotalTokens = chunks
              .map(chunk => calculateChunkTokenQuantity(chunk.chunk))
              .sum + totalTokens
            getChunksRecursively(
              offset + pageSize,
              acc ++ chunks,
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
      acc: List[SimilarChunk]
  ): List[SimilarChunk] =
    remainingChunks match
      case head :: tail
          if totalTokens + calculateChunkTokenQuantity(
            head.chunk
          ) <= tokenLimit =>
        loop(
          tail,
          totalTokens + calculateChunkTokenQuantity(head.chunk),
          head :: acc
        )
      case _ =>
        acc.reverse

  loop(chunks, 0, List.empty)
