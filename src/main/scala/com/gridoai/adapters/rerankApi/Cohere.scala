package com.gridoai.adapters.rerankApi

import org.slf4j.LoggerFactory
import com.gridoai.adapters.HttpClient
import cats.effect._
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import sttp.model.Header
import sttp.model.MediaType
import com.gridoai.adapters.HttpClient
import com.gridoai.adapters.sendReq
import concurrent.duration.DurationInt
import com.gridoai.utils._
import cats.implicits._
import com.gridoai.domain.Chunk
import com.gridoai.domain.RelevantChunk

object CohereClient:

  case class RerankRequest(
      query: String,
      documents: List[String],
      model: String = "rerank-multilingual-v2.0"
  )

  case class Result(index: Integer, relevance_score: Float)

  case class RerankResponse(id: String, results: List[Result])

  val logger = LoggerFactory.getLogger(getClass.getName)
  def apply(httpClient: HttpClient, apiKey: String) = new RerankAPI[IO]:
    private val authHeader = Header("Authorization", s"Bearer $apiKey")
    def rerank(
        payload: RerankPayload
    ): IO[Either[String, List[RelevantChunk]]] =
      val request = RerankRequest(
        query = payload.query,
        documents = payload.chunks.map(c => s"${c.documentName}: ${c.content}")
      )
      val response = httpClient
        .post("/v1/rerank")
        .body(request.asJson.noSpaces)
        .header(authHeader)
        .contentType(MediaType.ApplicationJson)
        .sendReq(retries = 4, retryDelay = 1.seconds)
        .map(
          _.body.flatMap(decode[RerankResponse](_))
        )
        .timeoutTo(20.seconds, IO.pure(Left("Cohere API Timeout")))
        |> attempt
      response.map:
        case Right(r) =>
          r.results
            .sortBy(_.relevance_score)
            .reverse
            .map: res =>
              RelevantChunk(
                chunk = payload.chunks.apply(res.index),
                relevance = res.relevance_score
              )
            .traceFn: e =>
              s"input batch size: ${payload.chunks.length}, output batch size: ${e.length}"
            .asRight
        case Left(e) => (Left(e.toString()))
