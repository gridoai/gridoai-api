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

case class GridoAIMLEmbeddingRequest(
    texts: List[String],
    instruction: String,
    model: String = "multilingual-e5-base"
)

case class MessageResponse[T](message: T)

object GridoAIML extends EmbeddingAPI[IO]:
  val Http = HttpClient(embeddingApiEndpoint)

  def embedChat(text: String): IO[Either[String, Embedding]] =
    embed(
      text,
      "query"
    )
  def embedChunks(chunks: List[Chunk]): IO[Either[String, List[Embedding]]] =
    embedMany(
      chunks.map(_.content),
      "passage"
    )

  def embedMany(
      texts: List[String],
      instruction: String
  ): IO[Either[String, List[Embedding]]] =
    println(s"trying to get ${texts.length} embeddings using GridoAIML")
    executeByParts(embedLessThan8(instruction), 8)(texts)

  def embedLessThan8(instruction: String)(
      texts: List[String]
  ): IO[Either[String, List[Embedding]]] =
    val body = GridoAIMLEmbeddingRequest(texts, instruction).asJson.noSpaces
    Http
      .post(f"/embed")
      .headers(Map("Content-Type" -> "application/json"))
      .body(body)
      .sendReq()
      .map(
        _.body.flatMap(
          decode[MessageResponse[List[List[Float]]]](_).left.map(_.getMessage())
        )
      )
      .mapRight(
        _.message.map(vec =>
          Embedding(vector = vec, model = EmbeddingModel.MultilingualE5Base)
        )
      ) |> attempt

  def embed(
      text: String,
      instruction: String
  ): IO[Either[String, Embedding]] = traceMappable("embed"):
    embedMany(List(text), instruction).map(_.map(_.head))
