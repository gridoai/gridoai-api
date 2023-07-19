package com.gridoai.services.doc

import java.util.UUID
import cats.effect.IO
import cats.implicits._

import com.gridoai.domain.*
import com.gridoai.utils.*
import com.gridoai.adapters.embeddingApi.*
import com.gridoai.models.DocDB

def calculateTokenQuantity(content: String): Int =
  // we are overestimating the amount of tokens
  content.split(" ").length * 2

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
  chunkContent(document.content, 200, 100).map(content =>
    Chunk(
      documentUid = document.uid,
      documentName = document.name,
      documentSource = document.source,
      uid = UUID.randomUUID(),
      content = content,
      tokenQuantity = calculateTokenQuantity(content)
    )
  )

def embedChunks(embedding: EmbeddingAPI[IO])(
    chunks: List[Chunk]
): IO[Either[String, List[ChunkWithEmbedding]]] =
  chunks
    .traverse(chunk =>
      embedding
        .embed(chunk.content)
        .mapRight(embed => ChunkWithEmbedding(chunk, embed))
    )
    .map(partitionEithers)
    .mapLeft(x => x.mkString(","))

def makeAndStoreChunks(
    embedding: EmbeddingAPI[IO],
    orgId: String,
    role: String
)(document: Document)(implicit db: DocDB[IO]) =
  val embededChunks = makeChunks(document) |> embedChunks(embedding)
  embededChunks.flatMapRight(db.addChunks(orgId, role))

def getChunks(
    tokenLimit: Int,
    orgId: String,
    role: String,
    pageSize: Int = 100
)(
    vec: Embedding
)(implicit
    db: DocDB[IO]
): IO[Either[String, List[SimilarChunk]]] =

  def getChunksRecursively(
      offset: Int,
      tokenLimit: Int
  ): IO[Either[String, List[SimilarChunk]]] =
    val similarChunks = db.getNearChunks(vec, offset, pageSize, orgId, role)
    similarChunks.flatMapRight: chunks =>
      val tokenQuantity = chunks.map(_.chunk.tokenQuantity).sum
      val chunkQuantity = chunks.length
      if (chunkQuantity < pageSize) Right(chunks).pure[IO]
      else if (tokenQuantity > tokenLimit)
        Right(filterExcessTokens(chunks, tokenLimit)).pure[IO]
      else
        getChunksRecursively(
          pageSize + offset,
          tokenLimit - tokenQuantity
        ).mapRight(newChunks => chunks ++ newChunks)

  getChunksRecursively(0, tokenLimit)

def filterExcessTokens(
    chunks: List[SimilarChunk],
    tokenLimit: Int
): List[SimilarChunk] =
  chunks
    .scanLeft((0, chunks.head)) { case ((cumulativeTokens, _), chunk) =>
      (cumulativeTokens + chunk.chunk.tokenQuantity, chunk)
    }
    .filter((cumulativeTokens, _) => cumulativeTokens < tokenLimit)
    .map(_._2)

def filterChunksBySize(
    chunks: List[Chunk],
    maxTokens: Int = 3000
): List[Chunk] =
  chunks
    .foldLeft((List.empty[Chunk], 0)):
      case ((acc, size), chunk) =>
        if (size + chunk.content.length <= maxTokens * 4)
          (chunk :: acc, size + chunk.content.length)
        else (acc, size)
    ._1
    .reverse
