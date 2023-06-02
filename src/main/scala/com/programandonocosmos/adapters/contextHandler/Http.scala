package com.programandonocosmos.adapters.contextHandler
import cats.effect.IO
import com.programandonocosmos.adapters.*
import com.programandonocosmos.utils.*
import io.circe._
import io.circe.generic.auto._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import sttp.client3._
import sttp.client3._
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.model.StatusCode

val endpoint = sys.env.getOrElse(
  "CONTEXT_HANDLER_ENDPOINT",
  "http://127.0.0.1:8000"
)

case class MessageResponse[T](message: T)

type DocResponseItem = (String, Float)
type DocResponse = List[DocResponseItem]

object DocumentApiClient:
  val Http = HttpClient(endpoint)
  def write(
      uid: String,
      text: String
  ): IO[Either[String | io.circe.Error, MessageResponse[List[Float]]]] =
    Http
      .post("/write")
      .body(Map("uid" -> uid, "text" -> text))
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
      .trace("Response: ")
      .map(_.body.trace.flatMap(decode[MessageResponse[DocResponse]]))
