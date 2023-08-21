package com.gridoai.adapters.llm.chatGPT

import com.gridoai.adapters.*
import com.gridoai.adapters.llm.*
import com.gridoai.domain.*
import com.gridoai.utils.*
import dev.maxmelnyk.openaiscala.models.text.completions.chat._
import dev.maxmelnyk.openaiscala.client.OpenAIClient
import sttp.client3.*
import cats.MonadError
import cats.implicits.*
import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.ModelType

val ENC = Encodings
  .newDefaultEncodingRegistry()
  .getEncodingForModel(ModelType.GPT_3_5_TURBO)

object ChatGPTClient:
  val maxInputTokens = 3_000

  def messageFromToRole: MessageFrom => ChatCompletion.Message.Role =
    case MessageFrom.Bot  => ChatCompletion.Message.Role.Assistant
    case MessageFrom.User => ChatCompletion.Message.Role.User

  def messageToClientMessage(m: Message): ChatCompletion.Message =
    ChatCompletion.Message(
      role = messageFromToRole(m.from),
      content = m.message
    )

  def makePayloadWithContext(context: Option[String] = None)(
      messages: List[Message]
  ): (Seq[ChatCompletion.Message], ChatCompletionSettings) =
    val contextMessage = context match
      case Some(c) =>
        List(
          ChatCompletion.Message(
            role = ChatCompletion.Message.Role.System,
            content = c
          )
        )
      case None => List()
    val chat = messages.map(messageToClientMessage)
    val fullSeq = (contextMessage ++ chat).toSeq
    (fullSeq, ChatCompletionSettings())

  def calculateTokenQuantity = ENC.countTokens

  def calculateMessageTokenQuantity(message: Message): Int =
    calculateTokenQuantity(s"${message.from.toString()}: ${message.message}")

  def calculateMessagesTokenQuantity(messages: List[Message]): Int =
    messages
      .map(calculateMessageTokenQuantity)
      .sum

  def apply[F[_]](sttpBackend: SttpBackend[F, Any])(using
      MonadError[F, Throwable]
  ) = new LLM[F]:
    val client = OpenAIClient(sttpBackend)

    def getAnswer(
        llmOutput: F[ChatCompletion]
    ): F[Either[String, String]] =
      llmOutput.map(_.choices.head.message.content).map(Right(_)) |> attempt

    def calculateChunkTokenQuantity(chunk: Chunk): Int =
      calculateTokenQuantity(
        s"name: ${chunk.documentName}\ncontent: ${chunk.content}\n\n"
      )

    def askMaxTokens(
        messages: List[Message],
        basedOnDocsOnly: Boolean = true
    ): Int =
      val contextTokens = calculateTokenQuantity(
        baseContextPrompt(basedOnDocsOnly)
      )
      val messageTokens = calculateMessagesTokenQuantity(messages)
      val res = maxInputTokens - messageTokens - contextTokens
      println(s"askMaxTokens: $res")
      res

    def ask(chunks: List[Chunk], basedOnDocsOnly: Boolean = true)(
        messages: List[Message]
    ): F[Either[String, String]] =

      val mergedChunks = chunks
        .map(chunk =>
          s"name: ${chunk.documentName}\ncontent: ${chunk.content}\n\n"
        )
        .mkString("\n")
      val context = s"${baseContextPrompt(basedOnDocsOnly)}\n$mergedChunks"
      println(
        s"Total tokens in chunks: ${calculateTokenQuantity(mergedChunks)}"
      )
      println(
        s"Total tokens in messages: ${calculateMessagesTokenQuantity(messages)}"
      )
      messages
        |> makePayloadWithContext(Some(context))
        |> client.createChatCompletion
        |> getAnswer

    def buildQueryToSearchDocuments(
        messages: List[Message]
    ): F[Either[String, String]] =
      messages
        |> makePayloadWithContext(Some(buildQueryToSearchDocumentsPrompt))
        |> client.createChatCompletion
        |> getAnswer
