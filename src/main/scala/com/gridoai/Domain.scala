package com.gridoai.domain
import io.circe._
import io.circe.generic.semiauto._

import java.util.UUID

type UID = UUID

enum MessageFrom:
  case Bot, User

enum EmbeddingModel:
  case TextEmbeddingsAda002, TextEmbeddingsBert002,
    TextEmbeddingsBertMultilingual002, TextGecko, InstructorLarge, Mocked

def strToEmbedding(model: String): EmbeddingModel =
  model match
    case "text-embeddings-ada-002"  => EmbeddingModel.TextEmbeddingsAda002
    case "text-embeddings-bert-002" => EmbeddingModel.TextEmbeddingsBert002
    case "text-embeddings-bert-multilingual-002" =>
      EmbeddingModel.TextEmbeddingsBertMultilingual002
    case "text-gecko"       => EmbeddingModel.TextGecko
    case "instructor-large" => EmbeddingModel.InstructorLarge
    case "mocked"           => EmbeddingModel.Mocked

def embeddingToStr(model: EmbeddingModel): String =
  model match
    case EmbeddingModel.TextEmbeddingsAda002  => "text-embeddings-ada-002"
    case EmbeddingModel.TextEmbeddingsBert002 => "text-embeddings-bert-002"
    case EmbeddingModel.TextEmbeddingsBertMultilingual002 =>
      "text-embeddings-bert-multilingual-002"
    case EmbeddingModel.TextGecko       => "text-gecko"
    case EmbeddingModel.InstructorLarge => "instructor-large"
    case EmbeddingModel.Mocked          => "mocked"

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

case class Embedding(vector: List[Float], model: EmbeddingModel)

case class ChunkWithEmbedding(
    chunk: Chunk,
    embedding: Embedding
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
