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
  chunkContent(document.content, 500, 100).map(content =>
    Chunk(
      documentUid = document.uid,
      documentName = document.name,
      documentSource = document.source,
      uid = UUID.randomUUID(),
      content = content,
      tokenQuantity = content.filter(_ != ' ').length / 4
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
    calculateChunkTokenQuantity: Chunk => Int,
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
      val tokenQuantity = chunks.map(_.chunk |> calculateChunkTokenQuantity).sum
      val chunkQuantity = chunks.length
      if (chunkQuantity < pageSize) Right(chunks).pure[IO]
      else if (tokenQuantity > tokenLimit)
        Right(
          filterExcessTokens(chunks, calculateChunkTokenQuantity, tokenLimit)
        ).pure[IO]
      else
        getChunksRecursively(
          pageSize + offset,
          tokenLimit - tokenQuantity
        ).mapRight(newChunks => chunks ++ newChunks)

  getChunksRecursively(0, tokenLimit)

def filterExcessTokens(
    chunks: List[SimilarChunk],
    calculateChunkTokenQuantity: Chunk => Int,
    tokenLimit: Int
): List[SimilarChunk] =
  chunks
    .scanLeft((0, chunks.head)):
      case ((cumulativeTokens, _), chunk) =>
        (cumulativeTokens + calculateChunkTokenQuantity(chunk.chunk), chunk)
    .filter((cumulativeTokens, _) => cumulativeTokens < tokenLimit)
    .map(_._2)
