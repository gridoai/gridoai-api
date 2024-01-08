package com.gridoai.domain
import io.circe._
import io.circe.generic.semiauto._
import scala.util.Try
import java.util.UUID
import io.circe.derivation.{Configuration, ConfiguredEnumCodec}

object Plan:
  given Configuration =
    Configuration.default.withTransformConstructorNames(_.toLowerCase)

enum Plan derives ConfiguredEnumCodec:
  case Free, Starter, Pro, Enterprise, Individual

import Plan._
def getMaxUsersByPlan: Plan => Option[Int] =
  case Free | Individual => Some(1)
  case Starter           => Some(10)
  case Pro               => Some(100)
  case Enterprise        => None

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
  case WhatsApp(mediaId: String)

def strToSource(source: String): Either[String, Source] =
  source match
    case "Upload"        => Right(Source.Upload)
    case "CreateButton"  => Right(Source.CreateButton)
    case s"GDrive($s)"   => Right(Source.GDrive(s))
    case s"WhatsApp($s)" => Right(Source.WhatsApp(s))
    case s =>
      Left(
        s"Failed to parse source type: $s (Supported patterns are: Upload, CreateButton, GDrive(fileId), WhatsApp(mediaId))"
      )

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

case class RelevantChunk(
    chunk: Chunk,
    relevance: Float
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

case class CreateMessage(
    from: MessageFrom,
    message: String
):
  def toMessage = Message(from, message)

case class Message(
    from: MessageFrom,
    message: String,
    id: String = UUID.randomUUID.toString,
    timestamp: Long = System.currentTimeMillis / 1000
):
  def removeMetadata = CreateMessage(from, message)

case class AskPayload(
    messages: List[CreateMessage],
    basedOnDocsOnly: Boolean,
    scope: Option[List[UID]],
    useActions: Boolean = false
)

case class SearchPayload(
    queries: List[String],
    tokenLimit: Int,
    llmName: String,
    scope: Option[List[UID]]
)

enum SearchStatus:
  case Started, Success, Failure

case class SearchReport(
    queries: List[String],
    status: SearchStatus
)

case class AskResponse(message: String, sources: List[String])

case class PaginatedResponse[T](data: T, total: Int)

enum UploadStatus:
  case Failure, Success, Scheduled, Processing

enum MessageInterfacePayload:
  case MessageReceived(
      id: String,
      from: String,
      to: String,
      content: String,
      timestamp: Long
  )
  case FileUpload(
      from: String,
      to: String,
      mediaId: String,
      filename: String,
      mimeType: String
  )
  case StatusChanged

enum WhatsAppState:
  case NotAuthenticated, WaitingEmail, Authenticated
