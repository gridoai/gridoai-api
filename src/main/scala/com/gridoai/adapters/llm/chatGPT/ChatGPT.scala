package com.gridoai.adapters.llm.chatGPT

import dev.maxmelnyk.openaiscala.models.text.completions.chat._
import dev.maxmelnyk.openaiscala.client.OpenAIClient
import sttp.client3._
import cats.MonadError
import cats.implicits._
import cats.data.EitherT
import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.ModelType
import org.slf4j.LoggerFactory

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
    val inputTokens = fullSeq.map(_.content |> calculateTokenQuantity).sum
    logger.info(s"messages: $messages, context: ${context}")
    (
      fullSeq,
      ChatCompletionSettings(
        model = "gpt-3.5-turbo-16k",
        n = Some(1),
        maxTokens = Some(16_000 - inputTokens)
      )
    )

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
    val client = OpenAIClient(
      getEnv("OPENAI_API_KEY"),
      Some(getEnv("OPENAI_ORG_ID"))
    )(sttpBackend)

    def getAnswerFromChat(
        llmOutput: F[ChatCompletion]
    ): EitherT[F, String, String] =
      llmOutput
        .map(_.choices.head.message.content)
        .map(Right(_))
        .asEitherT
        .attempt

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
    ): EitherT[F, String, String] =
      askOrAnswer(chunks, basedOnDocsOnly, messages, searchedBefore, false)

    def ask(
        chunks: List[Chunk],
        basedOnDocsOnly: Boolean,
        messages: List[Message],
        searchedBefore: Boolean
    ): EitherT[F, String, String] =
      askOrAnswer(chunks, basedOnDocsOnly, messages, searchedBefore, true)

    def askOrAnswer(
        chunks: List[Chunk],
        basedOnDocsOnly: Boolean,
        messages: List[Message],
        searchedBefore: Boolean,
        askUser: Boolean
    ): EitherT[F, String, String] =

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
        |> makePayloadWithContext(Some(context))
        |> client.createChatCompletion
        |> getAnswerFromChat

    def chooseAction(
        messages: List[Message],
        queries: List[String],
        chunks: List[Chunk],
        options: List[Action]
    ): EitherT[F, String, Action] =
      val prompt = chooseActionPrompt(chunks, messages, options)
      (
        Seq(
          ChatCompletion.Message(
            role = ChatCompletion.Message.Role.System,
            content = prompt
          )
        ),
        ChatCompletionSettings(maxTokens = Some(1), n = Some(1))
      )
        |> client.createChatCompletion
        |> getAnswerFromChat
        |> (_.value.map(_.flatMap(strToAction(options))).asEitherT)

    def buildQueriesToSearchDocuments(
        messages: List[Message],
        lastQueries: List[String],
        lastChunks: List[Chunk]
    ): EitherT[F, String, List[String]] =
      val prompt =
        buildQueriesToSearchDocumentsPrompt(
          messages.dropRight(1),
          lastQueries,
          lastChunks
        )

      val fullConversation = ChatCompletion.Message(
        role = ChatCompletion.Message.Role.System,
        content = prompt
      ) :: (buildQueriesExample :+ Message(
        from = MessageFrom.User,
        message = mergeMessages(messages)
      )).map(messageToClientMessage)

      logger.info(s"Prompt to build query: $prompt")
      (
        fullConversation,
        ChatCompletionSettings(maxTokens = Some(1_000), n = Some(1))
      )
        |> client.createChatCompletion
        |> getAnswerFromChat
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
