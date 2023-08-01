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

  def embedChat(text: String): IO[Either[String, Embedding]] =
    embed(text)

  def embedChunks(chunks: List[Chunk]): IO[Either[String, List[Embedding]]] =
    embedMany(chunks.map(_.content))

  def embedMany(texts: List[String]): IO[Either[String, List[Embedding]]] =
    Http
      .post(f"/embed")
      .headers(Map("Content-Type" -> "application/json"))
      .body(Map("texts" -> texts).asJson.toString)
      .sendReq()
      .map(
        _.body.flatMap(
          decode[MessageResponse[List[List[Float]]]](_).left.map(_.getMessage())
        )
      )
      .mapRight(
        _.message.map(vec =>
          Embedding(vector = vec, model = EmbeddingModel.InstructorLarge)
        )
      ) |> attempt

  def embed(
      text: String
  ): IO[Either[String, Embedding]] = traceMappable("embed"):
    embedMany(List(text)).map(_.map(_.head))
