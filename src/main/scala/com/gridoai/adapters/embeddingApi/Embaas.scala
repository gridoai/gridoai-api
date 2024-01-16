package com.gridoai.adapters.embeddingApi

import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import cats.effect.IO
import sttp.model.Header
import cats.implicits._
import sttp.model.MediaType
import concurrent.duration.DurationInt
import cats.data.EitherT

import com.gridoai.adapters.HttpClient
import com.gridoai.domain.Embedding
import com.gridoai.domain.Chunk
import com.gridoai.adapters.sendReq
import com.gridoai.domain.EmbeddingModel
import com.gridoai.utils._

case class EmbeddingRequest(
    texts: List[String],
    instruction: String,
    model: String = "multilingual-e5-base"
)
case class EmbeddingResponseData(
    embedding: List[Float],
    index: Int
)
case class EmbeddingResponse(
    data: List[EmbeddingResponseData]
)

object EmbaasClient:
  def apply(httpClient: HttpClient, apiKey: String) = new EmbeddingAPI[IO]:

    def embedChats(texts: List[String]): EitherT[IO, String, List[Embedding]] =
      embed(
        texts,
        "query"
      )
    def embedChunks(chunks: List[Chunk]): EitherT[IO, String, List[Embedding]] =
      embedMany(
        chunks.map(_.content),
        "passage"
      )

    def embed(
        texts: List[String],
        instruction: String
    ): EitherT[IO, String, List[Embedding]] =
      embedMany(texts, instruction)

    private val authHeader = Header("Authorization", s"Bearer $apiKey")

    def embedMany(
        texts: List[String],
        instruction: String
    ): EitherT[IO, String, List[Embedding]] =
      println(s"Trying to get ${texts.length} embeddings using Embaas")
      executeByParts(embedLessThan200(instruction), 200)(texts)

    def embedLessThan200(instruction: String)(
        texts: List[String]
    ): EitherT[IO, String, List[Embedding]] =
      val request = EmbeddingRequest(texts, instruction)
      val response = httpClient
        .post("/v1/embeddings/")
        .body(request.asJson.noSpaces)
        .header(authHeader)
        .contentType(MediaType.ApplicationJson)
        .sendReq(retries = 4, retryDelay = 1.seconds)
        .map(
          _.body.flatMap(decode[EmbeddingResponse](_))
        )
        .timeoutTo(20.seconds, IO.pure(Left("Embaas API Timeout")))

      response
        .mapRight: r =>
          r.data
            .sortBy(_.index)
            .map(d =>
              (Embedding(d.embedding, EmbeddingModel.MultilingualE5Base))
            )
            .traceFn: e =>
              s"input batch size: ${texts.length}, output batch size: ${e.length}"
        .mapLeft(_.toString)
        .asEitherT
        .attempt
