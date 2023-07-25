package com.gridoai.domain
import io.circe._
import io.circe.generic.semiauto._
import scala.util.Try
import java.util.UUID

type UID = UUID

enum MessageFrom:
  case Bot, User

enum EmbeddingModel:
  case TextEmbeddingsAda002, TextEmbeddingsBert002,
    TextEmbeddingsBertMultilingual002, TextGecko, InstructorLarge, Mocked

def strToEmbedding(model: String): EmbeddingModel =
  Try(EmbeddingModel.valueOf(model)).getOrElse(EmbeddingModel.Mocked)

def embeddingToStr(model: EmbeddingModel): String =
  model.toString()

enum LLMModel:
  case Palm2, Gpt35Turbo, Mocked

def strToLLM(model: String): LLMModel =
  Try(LLMModel.valueOf(model)).getOrElse(LLMModel.Mocked)

def llmToStr(model: LLMModel): String =
  model.toString()

case class Document(
    uid: UID,
    name: String,
    source: String,
    content: String
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
  def toDocument(uid: UID) =
    Document(
      uid,
      name,
      source,
      content
    )

case class Message(from: MessageFrom, message: String)

case class PaginatedResponse[T](data: T, total: Int)
