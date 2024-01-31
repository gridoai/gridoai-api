package com.gridoai.adapters.llm.chatGPT

import sttp.client3._
import cats.MonadError
import cats.implicits._
import cats.data.EitherT
import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.ModelType
import org.slf4j.LoggerFactory
import java.util.ArrayList
import collection.JavaConverters.seqAsJavaListConverter
import com.azure.ai.openai.models.ChatRequestSystemMessage
import com.azure.ai.openai.models.ChatRequestUserMessage
import com.azure.ai.openai.models.ChatRequestAssistantMessage
import com.azure.ai.openai.OpenAIClientBuilder
import com.azure.core.credential.KeyCredential
import com.azure.ai.openai.models.ChatCompletionsOptions
import com.azure.ai.openai.models.ChatRequestMessage
import fs2.interop.reactivestreams._
import fs2.Stream
import cats.effect.IO
import cats.effect.Async

import com.gridoai.adapters._
import com.gridoai.adapters.llm._
import com.gridoai.domain._
import com.gridoai.utils._

val ENC_GPT35TURBO = Encodings
  .newDefaultEncodingRegistry()
  .getEncodingForModel(ModelType.GPT_3_5_TURBO_16K)

object ChatGPTClient:
  val logger = LoggerFactory.getLogger(getClass.getName)

  val maxInputTokens = 10_000

  def messageToClientMessage(m: Message): ChatRequestMessage =
    m.from match
      case MessageFrom.Bot    => ChatRequestAssistantMessage(m.message)
      case MessageFrom.User   => ChatRequestUserMessage(m.message)
      case MessageFrom.System => ChatRequestSystemMessage(m.message)

  def makePayloadWithContext(context: String)(
      messages: List[Message]
  ): List[Message] =
    val contextMessage = List(
      Message(
        from = MessageFrom.System,
        message = context
      )
    )
    logger.info(s"messages: $messages, context: ${context}")
    (contextMessage ++ messages)

  def calculateTokenQuantity = ENC_GPT35TURBO.countTokens

  def calculateMessageTokenQuantity(message: Message): Int =
    calculateTokenQuantity(s"${message.from.toString()}: ${message.message}")

  def calculateMessagesTokenQuantity(messages: List[Message]): Int =
    messages
      .map(calculateMessageTokenQuantity)
      .sum

  def apply[F[_]: Async](sttpBackend: SttpBackend[F, Any])(using
      MonadError[F, Throwable]
  ) = new LLM[F]:
    val client = new OpenAIClientBuilder()
      .credential(new KeyCredential(getEnv("OPENAI_API_KEY")))
      .buildAsyncClient()

    def createChatCompletion(maxTokens: Option[Int] = None)(
        messages: List[Message]
    ): Stream[F, Either[String, String]] =

      val chatMessages = messages.map(messageToClientMessage).asJava
      val options = ChatCompletionsOptions(chatMessages)
      options.setMaxTokens(
        maxTokens.getOrElse(
          messages.map(_.message |> calculateTokenQuantity).sum
        )
      )

      val chatCompletionsStream =
        client.getChatCompletionsStream("gpt-3.5-turbo-16k", options)

      chatCompletionsStream
        .toStreamBuffered[F](1)
        .map(_.getChoices().get(0).getDelta().getContent())
        .filter(_ != null)
        .attempt
        .leftMap(_.toString)

    def calculateChunkTokenQuantity(chunk: Chunk): Int =
      chunk |> chunkToStr |> calculateTokenQuantity

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
      logger.info(s"askMaxTokens: $res")
      res

    def answer(
        chunks: List[Chunk],
        basedOnDocsOnly: Boolean,
        messages: List[Message],
        searchedBefore: Boolean
    ): Stream[F, Either[String, String]] =
      askOrAnswer(chunks, basedOnDocsOnly, messages, searchedBefore, false)

    def ask(
        chunks: List[Chunk],
        basedOnDocsOnly: Boolean,
        messages: List[Message],
        searchedBefore: Boolean
    ): Stream[F, Either[String, String]] =
      askOrAnswer(chunks, basedOnDocsOnly, messages, searchedBefore, true)

    def askOrAnswer(
        chunks: List[Chunk],
        basedOnDocsOnly: Boolean,
        messages: List[Message],
        searchedBefore: Boolean,
        askUser: Boolean
    ): Stream[F, Either[String, String]] =

      val mergedChunks = mergeChunks(chunks)
      val context =
        s"${baseContextPrompt(basedOnDocsOnly, askUser, searchedBefore)}\n\n$mergedChunks"
      logger.info(
        s"Total tokens in chunks: ${calculateTokenQuantity(mergedChunks)}"
      )
      logger.info(
        s"Total tokens in messages: ${calculateMessagesTokenQuantity(messages)}"
      )

      messages
        |> makePayloadWithContext(context)
        |> createChatCompletion()

    def chooseAction(
        messages: List[Message],
        queries: List[String],
        chunks: List[Chunk],
        options: List[Action]
    ): EitherT[F, String, Action] =
      val prompt = chooseActionPrompt(chunks, messages, options)
      List(
        Message(
          from = MessageFrom.System,
          message = prompt
        )
      )
        |> createChatCompletion(Some(1))
        |> (_.compileOutput.leftMap(_.mkString(", ")).map(_.mkString))
        |> (_.subflatMap(strToAction(options)))

    def buildQueriesToSearchDocuments(
        messages: List[Message],
        lastQueries: List[String],
        lastChunks: List[Chunk]
    ): EitherT[F, String, List[String]] =
      val prompt =
        buildQueriesToSearchDocumentsPrompt(
          lastQueries,
          lastChunks
        )

      logger.info(s"Prompt to build query: $prompt")
      (buildQueriesExample :+ Message(
        from = MessageFrom.User,
        message = mergeMessages(messages)
      ))
        |> makePayloadWithContext(prompt)
        |> createChatCompletion(Some(1_000))
        |> (_.compileOutput.leftMap(_.mkString(", ")).map(_.mkString))
        |> (_.map(
          _.split("\n").map(_.trim).filter(!_.isEmpty).take(3).toList
        ))

    def strToAction(options: List[Action])(
        llmOutput: String
    ): Either[String, Action] =
      llmOutput
        .trim()
        .toIntOption
        .map(_ - 1)
        .flatMap(options.get) match
        case None =>
          logger.info(s"bad action: $llmOutput")
          Left("Invalid LLM output")
        case Some(action) => Right(action)
