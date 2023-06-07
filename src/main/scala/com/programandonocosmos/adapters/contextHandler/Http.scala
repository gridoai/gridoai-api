package com.programandonocosmos.adapters.contextHandler
import cats.effect.IO
import com.programandonocosmos.adapters.*
import com.programandonocosmos.mock.mockedDoc
import com.programandonocosmos.utils.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.generic.semiauto.*
import io.circe.parser.*
import io.circe.syntax.*
import sttp.client3.*
import sttp.client3.*
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.model.{Header, StatusCode}

val contextHandlerEndpoint = sys.env.getOrElse(
  "CONTEXT_HANDLER_ENDPOINT",
  "http://127.0.0.1:8000"
)

case class MessageResponse[T](message: T)

type DocResponseItem = (String, Float)
type DocResponse = List[DocResponseItem]
case class NewDocBody(uid: String, text: String)
trait DocumentApiClient:
  def write(
      uid: String,
      text: String
  ): IO[Either[String | io.circe.Error, MessageResponse[List[Float]]]]

  def delete(uid: String): IO[StatusCode]

  def neardocs(
      text: String
  ): IO[Either[String | io.circe.Error, MessageResponse[DocResponse]]]

object DocumentApiClientHttp extends DocumentApiClient:
  val Http = HttpClient(contextHandlerEndpoint)

  def write(
      uid: String,
      text: String
  ): IO[Either[String | io.circe.Error, MessageResponse[List[Float]]]] =
    Http
      .post("/write")
      .body(NewDocBody(uid, text).asJson.toString)
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
      .map(_.body.trace.flatMap(decode[MessageResponse[DocResponse]]))

object MockDocumentApiClient extends DocumentApiClient:
  private val mockResponse: MessageResponse[List[Float]] = MessageResponse(
    List(1.0f, 2.0f, 3.0f)
  )
  val mockDocResponse: MessageResponse[DocResponse] = MessageResponse(
    List((mockedDoc.uid.toString, 0.5f), (mockedDoc.uid.toString, 0.3f))
  )

  override def write(
      uid: String,
      text: String
  ): IO[Either[String | io.circe.Error, MessageResponse[List[Float]]]] =
    IO.pure(Right(mockResponse))

  override def delete(uid: String): IO[StatusCode] =
    IO.pure(StatusCode.Ok)

  override def neardocs(
      text: String
  ): IO[Either[String | io.circe.Error, MessageResponse[DocResponse]]] =
    IO.pure(Right(mockDocResponse))

val DocumentApiClient =
  if (sys.env.get("ENV") == Some("TEST")) then MockDocumentApiClient
  else DocumentApiClientHttp
