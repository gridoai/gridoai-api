package com.gridoai.adapters.llm
import cats.effect.IO
import com.google.auth.oauth2.GoogleCredentials
import com.gridoai.adapters.*
import com.gridoai.domain.*
import com.gridoai.utils._
import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.syntax.*
import com.gridoai.utils.attempt
import com.gridoai.utils.|>
import org.checkerframework.checker.units.qual.m

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

  def call(data: Data): IO[Either[String, Palm2Response]] =
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
      ) |> attempt

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
      llmOutput: IO[Either[String, Palm2Response]]
  ): IO[Either[String, String]] =
    llmOutput.mapRight(_.predictions.head.candidates.head.content)

  def calculateTokenQuantity(text: String): Int =
    text.filter(c => c != ' ').length / 4

  def calculateChunkTokenQuantity(chunk: Chunk): Int =
    val contentTokens = 8 + calculateTokenQuantity(chunk.content)
    val nameTokens = 5 + calculateTokenQuantity(chunk.documentName)
    contentTokens + nameTokens

  def askMaxTokens(messages: List[Message]): Int =
    val messageTokens =
      messages.map(m => 10 + calculateTokenQuantity(m.message)).sum
    maxInputTokens - messageTokens

  def ask(chunks: List[Chunk])(
      messages: List[Message]
  ): IO[Either[String, String]] =
    val mergedChunks = chunks
      .map(chunk =>
        s"name: ${chunk.documentName}\ncontent: ${chunk.content}\n\n"
      )
      .mkString("\n")
    val context = s"$baseContextPrompt\n$mergedChunks"
    messages |> makePayloadWithContext(context) |> call |> getAnswer

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
    singleMessage |> makePayloadWithContext(
      "",
      topP = 0.95,
      topK = 40
    ) |> call |> getAnswer
