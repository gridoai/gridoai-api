package com.gridoai.adapters.llm
import cats.effect.IO
import com.google.auth.oauth2.GoogleCredentials
import com.gridoai.adapters.*
import com.gridoai.domain.*
import com.gridoai.utils.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.syntax.*
import com.gridoai.utils.attempt
import com.gridoai.utils.|>

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
      temperature: Double = 0.2,
      topP: Double = 0.8,
      topK: Int = 10
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
        maxOutputTokens = 512,
        topP = topP,
        topK = topK
      )
    )

  def getAnswer(
      llmOutput: IO[Either[String, Palm2Response]]
  ): IO[Either[String, String]] =
    llmOutput.mapRight(_.predictions.head.candidates.head.content)

  def ask(documents: List[Document])(
      messages: List[Message]
  ): IO[Either[String, String]] =
    val mergedDocuments = documents
      .map(doc =>
        s"document name: ${doc.name}\ndocument source: ${doc.source}\ndocument content: ${doc.content}"
      )
      .mkString("\n")
    val context = s"$baseContextPrompt\n$mergedDocuments"
    messages |> makePayloadWithContext(context) |> call |> getAnswer

  def mergeMessages(messages: List[Message]): IO[Either[String, String]] =

    val mergedMessages =
      messages.init
        .map(m => s"${m.from.toString()}: ${m.message}")
        .mkString("\n")
    val singleMessage = List(
      Message(
        from = MessageFrom.User,
        message =
          s"Provide a laconic summary for the following conversation: $mergedMessages"
      )
    )
    singleMessage |> makePayloadWithContext(
      chatMergePrompt,
      topP = 0.95,
      topK = 40
    ) |> call |> getAnswer
