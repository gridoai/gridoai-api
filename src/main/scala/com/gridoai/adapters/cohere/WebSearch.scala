package com.gridoai.adapters.cohere
import scala.concurrent.duration._
import sttp.model.Header
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import sttp.model.MediaType
import com.gridoai.adapters.sendReq
import com.gridoai.utils._

def searchWeb(query: String) = {
  val payload = Payload(message = query)
  println(("json payload", payload.asJson.spaces2))
  val token = sys.env("COHERE_API_KEY")
  val authHeader =
    Header("Authorization", s"Bearer ${token}")
  val req = httpClient
    .post("/v1/chat")
    .body(payload.asJson.spaces2)
  println(req.toCurl)
  val response = req
    .readTimeout(20.seconds)
    .header(authHeader)
    .contentType(MediaType.ApplicationJson)
    .sendReq(retries = 4, retryDelay = 1.seconds)
    .map(_.body.flatMap(decode[CohereLLMResponse](_)))
    .asEitherT
    .attempt

  response
    .traceFn: e =>
      s"Query: $query, Results found: ${e}"

}
