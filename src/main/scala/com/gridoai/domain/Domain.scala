package com.gridoai.domain
import io.circe._
import io.circe.generic.semiauto._
import scala.util.Try
import java.util.UUID
import io.circe.Codec
import io.circe.derivation.Configuration

given Configuration = Configuration.default
  .withDiscriminator("type")
  .withTransformConstructorNames(_.toLowerCase())

enum Plan:
  case Free, Starter, Pro, Enterprise

object Plan:
  given Codec[Plan] = Codec.AsObject.derivedConfigured

type UID = UUID

enum MessageFrom:
  case Bot, User

enum EmbeddingModel:
  case TextEmbeddingsAda002, TextEmbeddingsBert002,
    TextEmbeddingsBertMultilingual002, TextGecko, InstructorLarge,
    MultilingualE5Base, Mocked

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

enum Source:
  case Upload, CreateButton
  case GDrive(fileId: String)

def strToSource(source: String): Either[String, Source] =
  source match
    case "Upload"       => Right(Source.Upload)
    case "CreateButton" => Right(Source.CreateButton)
    case s if s.startsWith("GDrive(") =>
      Right(Source.GDrive(s.substring(7, s.length - 1)))
    case _ => Left("Source out of pattern.")

enum Action:
  case Ask, Answer, Search

case class Document(
    uid: UID,
    name: String,
    source: Source,
    content: String
)

case class Chunk(
    documentUid: UID,
    documentName: String,
    documentSource: Source,
    uid: UID,
    content: String,
    tokenQuantity: Int,
    startPos: Int,
    endPos: Int
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
    content: String
):
  def toDocument(uid: UID, source: Source) =
    Document(
      uid,
      name,
      source,
      content
    )

case class Message(from: MessageFrom, message: String)

case class AskPayload(
    messages: List[Message],
    basedOnDocsOnly: Boolean,
    scope: Option[List[UID]]
)

case class SearchPayload(
    query: String,
    tokenLimit: Int,
    llmName: String,
    scope: Option[List[UID]]
)

case class AskResponse(message: String, sources: List[String])

case class PaginatedResponse[T](data: T, total: Int)
