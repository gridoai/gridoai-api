package com.gridoai.adapters.llm

import cats.effect.IO
import com.gridoai.adapters.*
import com.gridoai.domain.*
import com.gridoai.utils.*
import dev.maxmelnyk.openaiscala.models.text.completions.chat._
import dev.maxmelnyk.openaiscala.client.OpenAIClient
import sttp.client3.*
import sttp.capabilities.WebSockets

object ChatGPTClient:
  val maxInputTokens = 2_000

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

  def getAnswer(
      llmOutput: IO[ChatCompletion]
  ): IO[Either[String, String]] =
    llmOutput.map(_.choices.head.message.content).map(Right(_)) |> attempt

  def calculateTokenQuantity(text: String): Int =
    // TODO: Improve GPT token counting to get more precise chunk allocations
    text.filter(_ != ' ').length / 4

  def calculateMessagesTokenQuantity(messages: List[Message]): Int =
    messages.map(m => 10 + calculateTokenQuantity(m.message)).sum

  def apply(sttpBackend: SttpBackend[IO, WebSockets]) = new LLM[IO]:
    val client = OpenAIClient(sttpBackend)

    def calculateChunkTokenQuantity(chunk: Chunk): Int =
      val contentTokens = 8 + calculateTokenQuantity(chunk.content)
      val nameTokens = 5 + calculateTokenQuantity(chunk.documentName)
      contentTokens + nameTokens

    def askMaxTokens(messages: List[Message]): Int =
      val contextTokens = calculateTokenQuantity(baseContextPrompt)
      val messageTokens = calculateMessagesTokenQuantity(messages)
      val res = maxInputTokens - messageTokens - contextTokens
      println(s"askMaxTokens: $res")
      res

    def ask(chunks: List[Chunk])(
        messages: List[Message]
    ): IO[Either[String, String]] =

      val mergedChunks = chunks
        .map(chunk =>
          s"name: ${chunk.documentName}\ncontent: ${chunk.content}\n\n"
        )
        .mkString("\n")
      val context = s"$baseContextPrompt\n$mergedChunks"
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

    def mergeMessages(messages: List[Message]): IO[Either[String, String]] =

      val mergedMessages =
        messages
          .map(m => s"${m.from.toString()}: ${m.message}")
          .mkString("\n")
      val singleMessage = List(
        Message(
          from = MessageFrom.User,
          message =
            s"$chatMergePrompt\n\nProvide a laconic summary for the following conversation: $mergedMessages"
        )
      )
      singleMessage
        |> makePayloadWithContext()
        |> client.createChatCompletion
        |> getAnswer
