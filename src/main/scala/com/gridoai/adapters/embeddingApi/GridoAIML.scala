package com.gridoai.adapters.embeddingApi

import cats.effect.IO
import com.gridoai.adapters.*
import com.gridoai.domain.*
import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.*
import io.circe.syntax.*
import cats.implicits.*
import com.gridoai.utils.*

val embeddingApiEndpoint = sys.env.getOrElse(
  "EMBEDDING_API_ENDPOINT",
  "http://127.0.0.1:8000"
)

case class MessageResponse[T](message: T)

object GridoAIML extends EmbeddingAPI[IO]:
  val Http = HttpClient(embeddingApiEndpoint)

  def embedMany(text: List[String]): IO[Either[String, List[Embedding]]] =
    text.map(embed).sequence.map(_.sequence)

  def embed(
      text: String
  ): IO[Either[String, Embedding]] = traceMappable("embed"):
    println(s"Calculating embedding for: ${text.slice(0, 20)}")
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
        Embedding(vector = x.message, model = EmbeddingModel.InstructorLarge)
      ) |> attempt
