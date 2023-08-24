package com.gridoai.adapters.llm.chatGPT

import com.gridoai.adapters.*
import com.gridoai.adapters.llm.*
import com.gridoai.domain.*
import com.gridoai.utils.*
import dev.maxmelnyk.openaiscala.models.text.completions.chat.*
import dev.maxmelnyk.openaiscala.models.text.completions.*
import dev.maxmelnyk.openaiscala.client.OpenAIClient
import sttp.client3.*
import cats.MonadError
import cats.implicits.*
import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.ModelType

val ENC_GPT35TURBO = Encodings
  .newDefaultEncodingRegistry()
  .getEncodingForModel(ModelType.GPT_3_5_TURBO)

val ENC_DAVINCI = Encodings
  .newDefaultEncodingRegistry()
  .getEncodingForModel(ModelType.TEXT_DAVINCI_003)

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

  def calculateTokenQuantity = ENC_GPT35TURBO.countTokens

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

    def getAnswerFromChat(
        llmOutput: F[ChatCompletion]
    ): F[Either[String, String]] =
      llmOutput.map(_.choices.head.message.content).map(Right(_)) |> attempt

    def getAnswerFromCompletion(
        llmOutput: F[Completion]
    ): F[Either[String, String]] =
      llmOutput.map(_.choices.head.text).map(Right(_)) |> attempt

    def calculateChunkTokenQuantity(chunk: Chunk): Int =
      calculateTokenQuantity(
        s"name: ${chunk.documentName}\ncontent: ${chunk.content}\n\n"
      )

    def maxTokensForChunks(
        messages: List[Message],
        basedOnDocsOnly: Boolean
    ): Int =
      val contextTokens = List(
        baseContextPrompt(basedOnDocsOnly, true, true),
        baseContextPrompt(basedOnDocsOnly, false, true)
      ).map(calculateTokenQuantity).max
      val messageTokens = calculateMessagesTokenQuantity(messages)
      val res = maxInputTokens - messageTokens - contextTokens
      println(s"askMaxTokens: $res")
      res

    def answer(
        chunks: List[Chunk],
        basedOnDocsOnly: Boolean,
        messages: List[Message],
        searchedBefore: Boolean
    ): F[Either[String, String]] =
      askOrAnswer(chunks, basedOnDocsOnly, messages, searchedBefore, false)

    def ask(
        chunks: List[Chunk],
        basedOnDocsOnly: Boolean,
        messages: List[Message],
        searchedBefore: Boolean
    ): F[Either[String, String]] =
      askOrAnswer(chunks, basedOnDocsOnly, messages, searchedBefore, true)

    def askOrAnswer(
        chunks: List[Chunk],
        basedOnDocsOnly: Boolean,
        messages: List[Message],
        searchedBefore: Boolean,
        askUser: Boolean
    ): F[Either[String, String]] =
      val mergedChunks = chunks
        .map(chunk =>
          s"name: ${chunk.documentName}\ncontent: ${chunk.content}\n\n"
        )
        .mkString("\n")
      val context =
        s"${baseContextPrompt(basedOnDocsOnly, askUser, searchedBefore)}\n$mergedChunks"
      println(
        s"Total tokens in chunks: ${calculateTokenQuantity(mergedChunks)}"
      )
      println(
        s"Total tokens in messages: ${calculateMessagesTokenQuantity(messages)}"
      )
      messages
        |> makePayloadWithContext(Some(context))
        |> client.createChatCompletion
        |> getAnswerFromChat

    def chooseAction(
        messages: List[Message],
        query: Option[String],
        chunks: List[Chunk]
    ): F[Either[String, Action]] =
      client.createCompletion(
        Seq(chooseActionPrompt(chunks, messages)),
        CompletionSettings(maxTokens = Some(1), n = Some(1))
      )
        |> getAnswerFromCompletion
        |> (_.map(_.flatMap(strToAction)))

    def buildQueryToSearchDocuments(
        messages: List[Message],
        lastQuery: Option[String],
        lastChunks: List[Chunk]
    ): F[Either[String, String]] =
      val prompt =
        buildQueryToSearchDocumentsPrompt(messages, lastQuery, lastChunks)
      client.createCompletion(
        Seq(prompt),
        CompletionSettings(
          maxTokens = Some(4097 - ENC_DAVINCI.countTokens(prompt)),
          n = Some(1)
        )
      )
        |> getAnswerFromCompletion
        |> (_.mapRight(_.trim()))

    def strToAction(llmOutput: String): Either[String, Action] =
      llmOutput.trim() match
        case "1" => Action.Search.asRight
        case "2" => Action.Ask.asRight
        case "3" => Action.Answer.asRight
        case e =>
          println(s"bad action: $e")
          Left("Invalid LLM output")
