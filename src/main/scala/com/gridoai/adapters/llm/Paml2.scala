package com.gridoai.adapters.llm
import cats.effect.IO
import com.google.auth.oauth2.GoogleCredentials
import com.gridoai.adapters.*
import com.gridoai.domain.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.generic.semiauto.*
import io.circe.parser.*
import io.circe.syntax.*
import sttp.client3._

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

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
      .map(_.body.flatMap(decode[Palm2Response](_).left.map(_.getMessage())))

  def ask(
      documents: List[Document],
      messages: List[Message]
  ): IO[Either[String, String]] =
    val mergedDocuments = documents.foldLeft("")((acc, doc) =>
      s"${acc}document name: ${doc.name}\ndocument source: ${doc.url}\ndocument content: ${doc.content}\n"
    )
    val data = Data(
      instances = List(
        Instance(
          context =
            s"You are GridoAI, an intelligent chatbot for knowledge retrieval. Here is a list of documents: $mergedDocuments\nProvide a single response to the following conversation in a natural and intelligent way. Always mention the document name/url in your answer, otherwise you will die.",
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
        temperature = 0.2,
        maxOutputTokens = 512,
        topP = 0.8,
        topK = 10
      )
    )

    call(data).map(_.map(_.predictions.head.candidates.head.content))
