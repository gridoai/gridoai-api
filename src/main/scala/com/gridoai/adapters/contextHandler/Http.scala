package com.gridoai.adapters.contextHandler
import cats.TraverseFilter.ops.toAllTraverseFilterOps
import cats.effect.IO
import cats.syntax.all.toFunctorFilterOps
import com.gridoai.adapters.*
import com.gridoai.mock.mockedDoc
import com.gridoai.utils.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.generic.semiauto.*
import io.circe.parser.*
import io.circe.syntax.*
import sttp.client3.*
import sttp.client3.*
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.model.Header
import sttp.model.StatusCode

val contextHandlerEndpoint = sys.env.getOrElse(
  "CONTEXT_HANDLER_ENDPOINT",
  "http://127.0.0.1:8000"
)

case class MessageResponse[T](message: T)

case class DocResponseItem(
    uid: String,
    distance: Float,
    content: String,
    path: Option[String] = None
)
type DocResponse = List[DocResponseItem]
case class NewDocBody(uid: String, content: String, path: String)
trait DocumentApiClient:
  def write(
      uid: String,
      content: String,
      path: String
  ): IO[Either[String | io.circe.Error, MessageResponse[List[Float]]]]

  def delete(uid: String): IO[StatusCode]

  def neardocs(
      text: String
  ): IO[Either[String | io.circe.Error, MessageResponse[DocResponse]]]

object DocumentApiClientHttp extends DocumentApiClient:
  val Http = HttpClient(contextHandlerEndpoint)

  def write(
      uid: String,
      content: String,
      path: String
  ): IO[Either[String | io.circe.Error, MessageResponse[List[Float]]]] =
    Http
      .post("/write")
      .body(NewDocBody(uid, content, path).asJson.toString)
      .contentType("application/json")
      .sendReq()
      .map(_.body.flatMap(decode[MessageResponse[List[Float]]]))

  def delete(uid: String): IO[StatusCode] =
    Http.get("/delete?uid=$uid").sendReq().map(_.code)

  def neardocs(
      text: String
  ): IO[Either[String | io.circe.Error, MessageResponse[DocResponse]]] =
    println("Searching near docs for: " + text)
    Http
      .get(f"/neardocs?text=$text")
      .sendReq()
      .map(_.body.flatMap(decode[MessageResponse[DocResponse]]))
      .map(
        _.map(res =>
          val docsWithoutUnrelated = res.message.filter(x => x._2 < 1.9f)
          println("Found near docs: " + docsWithoutUnrelated.map(_.path))
          keepTotalWordsUnderN(
            docsWithoutUnrelated,
            10_000
          )
        ).map(MessageResponse.apply)
      )

object MockDocumentApiClient extends DocumentApiClient:
  private val mockResponse: MessageResponse[List[Float]] = MessageResponse(
    List(1.0f, 2.0f, 3.0f)
  )
  val mockDocResponse: MessageResponse[DocResponse] = MessageResponse(
    List(
      DocResponseItem(
        mockedDoc.uid.toString,
        0.5f,
        "The sky is blue",
        Some("Sky observations")
      )
    )
  )

  override def write(
      uid: String,
      content: String,
      path: String
  ): IO[Either[String | io.circe.Error, MessageResponse[List[Float]]]] =
    IO.pure(Right(mockResponse))

  override def delete(uid: String): IO[StatusCode] =
    IO.pure(StatusCode.Ok)

  override def neardocs(
      text: String
  ): IO[Either[String | io.circe.Error, MessageResponse[DocResponse]]] =
    IO.pure(Right(mockDocResponse))

val DocumentApiClient =
  if sys.env.get("ENV").contains("TEST") then MockDocumentApiClient
  else DocumentApiClientHttp