package com.gridoai.domain
import io.circe._
import io.circe.generic.semiauto._

import java.util.UUID

type UID = UUID

enum MessageFrom:
  case Bot, User

case class Document(
    uid: UID,
    name: String,
    source: String,
    content: String,
    tokenQuantity: Int
)

case class Chunk(
    documentUid: UID,
    documentName: String,
    documentSource: String,
    uid: UID,
    content: String,
    tokenQuantity: Int
)

case class EmbeddingOutput(vector: List[Float], model: String)

case class ChunkWithEmbedding(
    chunk: Chunk,
    embedding: EmbeddingOutput
)

case class SimilarChunk(
    chunk: Chunk,
    distance: Float
)

case class DocumentCreationPayload(
    name: String,
    source: String,
    content: String
):
  def toDocument(
      uid: UID,
      tokenQuantity: Int
  ) = Document(
    uid,
    name,
    source,
    content,
    tokenQuantity
  )

case class Message(from: MessageFrom, message: String)

case class PaginatedResponse[T](data: T, total: Int)
