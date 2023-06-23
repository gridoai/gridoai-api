package com.gridoai.adapters.embeddingApi

import cats.effect.IO
import com.gridoai.adapters.*
import com.gridoai.domain.Embedding
import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.syntax.*

val embeddingApiEndpoint = sys.env.getOrElse(
  "EMBEDDING_API_ENDPOINT",
  "http://127.0.0.1:8000"
)

case class MessageResponse[T](message: T)

object GridoAIML extends EmbeddingAPI[IO]:
  val Http = HttpClient(embeddingApiEndpoint)

  def embed(
      text: String
  ): IO[Either[String, Embedding]] =
    println("Searching near docs for: " + text)
    Http
      .get(f"/embed?text=$text")
      .sendReq()
      .map(
        _.body.flatMap(
          decode[MessageResponse[Embedding]](_).left.map(_.getMessage())
        )
      )
      .map(_.map(_.message))
