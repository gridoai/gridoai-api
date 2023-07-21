package com.gridoai.adapters.embeddingApi

import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import cats.effect.IO
import com.gridoai.adapters.HttpClient
import com.gridoai.domain.Embedding
import sttp.model.Header
import com.gridoai.adapters.sendReq
import com.gridoai.domain.EmbeddingModel
import cats.implicits._
import com.gridoai.utils.*
import sttp.model.MediaType
import concurrent.duration.DurationInt
case class EmbeddingRequest(
    texts: List[String],
    model: String = "instructor-large",
    instruction: String = "represent this document"
)
case class EmbeddingResponseData(
    embedding: List[Float],
    index: Int
)
case class EmbeddingResponse(
    data: List[EmbeddingResponseData]
)

object EmbaasClient:
  def apply(httpClient: HttpClient, apiKey: String) =
    new EmbeddingAPI[IO]:

      def embed(text: String): IO[Either[String, Embedding]] =
        embedMany(List(text)).map(
          _.flatMap(_.headOption.toRight("Got no embedding from api"))
        )

      private val authHeader = Header("Authorization", s"Bearer $apiKey")

      def embedMany(
          texts: List[String]
      ): IO[Either[String, List[Embedding]]] =
        if texts.length <= 256 then embedLessThan256(texts)
        else
          embedLessThan256(texts.slice(0, 256)).flatMapRight(embeddings =>
            embedMany(texts.slice(256, texts.length)).mapRight(newEmbeddings =>
              embeddings ++ newEmbeddings
            )
          )

      def embedLessThan256(
          texts: List[String]
      ): IO[Either[String, List[Embedding]]] =
        val request = EmbeddingRequest(texts)
        val response = httpClient
          .post("/v1/embeddings/")
          .body(request.asJson.noSpaces)
          .header(authHeader)
          .contentType(MediaType.ApplicationJson)
          .sendReq(retries = 4, retryDelay = 1.seconds)
          .map(
            _.body.flatMap(decode[EmbeddingResponse](_))
          ) |> attempt
        response.map:
          case Right(r) =>
            r.data
              .sortBy(_.index)
              .map(d =>
                (Embedding(d.embedding, EmbeddingModel.InstructorLarge))
              )
              .asRight
          case Left(e) => (Left(e.toString()))
