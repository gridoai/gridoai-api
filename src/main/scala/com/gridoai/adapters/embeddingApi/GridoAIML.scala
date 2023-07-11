package com.gridoai.adapters.embeddingApi

import cats.effect.IO
import com.gridoai.adapters.*
import com.gridoai.domain.*
import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.*
import io.circe.syntax.*

import com.gridoai.utils.*

val embeddingApiEndpoint = sys.env.getOrElse(
  "EMBEDDING_API_ENDPOINT",
  "http://127.0.0.1:8000"
)

case class MessageResponse[T](message: T)

object GridoAIML extends EmbeddingAPI[IO]:
  val Http = HttpClient(embeddingApiEndpoint)

  def embed(
      text: String
  ): IO[Either[String, EmbeddingOutput]] =
    println("Searching near docs for: " + text)
    Http
      .post(f"/embed")
      .headers(Map("Content-Type" -> "application/json"))
      .body(Map("text" -> text).asJson.toString)
      .sendReq()
      .map(
        _.body.flatMap(
          decode[MessageResponse[List[Float]]](_).left.map(_.getMessage())
        )
      )
      .mapRight(x =>
        EmbeddingOutput(vector = x.message, model = "instructor-large")
      ) |> attempt
