package com.gridoai.adapters.llm.palm2

import cats.effect.IO
import cats.data.EitherT
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import fs2.Stream

import com.gridoai.adapters._
import com.gridoai.adapters.llm._
import com.gridoai.domain._
import com.gridoai.utils._
import com.gridoai.utils._

val apiEndpoint = "https://us-central1-aiplatform.googleapis.com"
val projectId = "lucid-arch-387422"
val modelId = "chat-bison@001"

case class PalmMessage(author: String, content: String)
case class Data(instances: List[Instance], parameters: Parameters)
case class Instance(
    context: String,
    examples: List[String],
    messages: List[PalmMessage]
)
case class Parameters(
    temperature: Double,
    maxOutputTokens: Int,
    topP: Double,
    topK: Int
)

case class Candidates(author: String, content: String)
case class Predictions(candidates: List[Candidates])
case class Palm2Response(predictions: List[Predictions])

object Paml2Client extends LLM[IO]:
  val maxInputTokens = 4_096

  val Http = HttpClient(apiEndpoint)
  var credentials = GoogleCredentials.getApplicationDefault()

  def getAccessToken() =
    credentials.refreshIfExpired()
    var token = credentials.getAccessToken()
    token.getTokenValue()

  def call(data: Data): EitherT[IO, String, Palm2Response] =
    val token = getAccessToken()

    val headers = Map(
      "Authorization" -> s"Bearer $token",
      "Content-Type" -> "application/json"
    )

    Http
      .post(
        s"/v1/projects/$projectId/locations/us-central1/publishers/google/models/$modelId:predict"
      )
      .headers(headers)
      .body(data.asJson.toString())
      .sendReq()
      .map(
        _.body.flatMap(decode[Palm2Response](_).left.map(_.getMessage()))
      )
      .asEitherT
      .attempt

  def makePayloadWithContext(
      context: String,
      temperature: Double = defaultTemperature,
      maxOutputTokens: Int = defaultMaxOutputTokens,
      topP: Double = defaultTopP,
      topK: Int = defaultTopK
  )(messages: List[Message]): Data =
    Data(
      instances = List(
        Instance(
          context = context,
          examples = List.empty,
          messages = messages.map(message =>
            PalmMessage(
              author = if (message.from == MessageFrom.User) "user" else "bot",
              content = message.message
            )
          )
        )
      ),
      parameters = Parameters(
        temperature = temperature,
        maxOutputTokens = maxOutputTokens,
        topP = topP,
        topK = topK
      )
    )

  def getAnswer(
      llmOutput: Palm2Response
  ): String =
    llmOutput.predictions.head.candidates.head.content

  def calculateTokenQuantity(text: String): Int =
    text.filter(_ != ' ').length / 4

  def calculateChunkTokenQuantity(chunk: Chunk): Int =
    val contentTokens = 8 + calculateTokenQuantity(chunk.content)
    val nameTokens = 5 + calculateTokenQuantity(chunk.documentName)
    contentTokens + nameTokens

  def calculateMessagesTokenQuantity(messages: List[Message]): Int =
    messages.map(m => 10 + calculateTokenQuantity(m.message)).sum

  def maxTokensForChunks(
      messages: List[Message],
      basedOnDocsOnly: Boolean
  ): Int =
    val contextTokens = calculateTokenQuantity(baseContextPrompt)
    val messageTokens = calculateMessagesTokenQuantity(messages)
    val res = maxInputTokens - messageTokens - contextTokens
    println(s"maxTokensForChunks: $res")
    res

  def ask(
      chunks: List[Chunk],
      basedOnDocsOnly: Boolean,
      messages: List[Message],
      searchedBefore: Boolean
  ): Stream[IO, Either[String, String]] =

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
    (messages |> makePayloadWithContext(context) |> call)
      .map(getAnswer)
      .toStream

  def answer(
      chunks: List[Chunk],
      basedOnDocsOnly: Boolean,
      messages: List[Message],
      searchedBefore: Boolean
  ): Stream[IO, Either[String, String]] =

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
    (messages |> makePayloadWithContext(context) |> call)
      .map(getAnswer)
      .toStream

  def chooseAction(
      messages: List[Message],
      queries: List[String],
      chunks: List[Chunk],
      options: List[Action]
  ): EitherT[IO, String, Action] =
    EitherT.rightT(Action.Search)

  def buildQueriesToSearchDocuments(
      messages: List[Message],
      lastQueries: List[String],
      lastChunks: List[Chunk]
  ): EitherT[IO, String, List[String]] =

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
    (singleMessage
      |> makePayloadWithContext("", topP = 0.95, topK = 40)
      |> call).map(getAnswer).map(List(_))
