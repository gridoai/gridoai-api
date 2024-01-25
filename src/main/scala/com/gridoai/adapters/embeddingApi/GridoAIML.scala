package com.gridoai.adapters.embeddingApi

import cats.effect.IO
import cats.implicits._
import cats.data.EitherT
import io.circe.generic.auto._
import io.circe.parser._
import io.circe._
import io.circe.syntax._
import org.slf4j.LoggerFactory
import concurrent.duration.DurationInt

import com.gridoai.utils._
import com.gridoai.adapters._
import com.gridoai.domain._

val embeddingApiEndpointSingle = sys.env.getOrElse(
  "EMBEDDING_API_ENDPOINT_SINGLE",
  "http://127.0.0.1:8000"
)

val embeddingApiEndpointBatch = sys.env.getOrElse(
  "EMBEDDING_API_ENDPOINT_BATCH",
  "http://127.0.0.1:8000"
)

case class GridoAIMLEmbeddingRequest(
    texts: List[String],
    instruction: String,
    model: String = "multilingual-e5-base-onnx"
)

case class MessageResponse[T](message: T)
val embedParallelism =
  sys.env.getOrElse("EMBEDDING_API_PARALLELISM", "4").toInt
val embedPartitionSize =
  sys.env.getOrElse("EMBEDDING_API_PARTITION_SIZE", "128").toInt

val _ = println(
  s"""embedParallelism: $embedParallelism
  embedPartitionSize: $embedPartitionSize
  embeddingApiEndpointSingle: $embeddingApiEndpointSingle
  embeddingApiEndpointBatch: $embeddingApiEndpointBatch"""
)
object GridoAIML extends EmbeddingAPI[IO]:
  val logger = LoggerFactory.getLogger(getClass.getName)
  val HttpSingle = HttpClient(embeddingApiEndpointSingle)
  val HttpBatch = HttpClient(embeddingApiEndpointBatch)

  def embedChats(texts: List[String]): EitherT[IO, String, List[Embedding]] =
    embed(
      texts,
      "query"
    )
  def embedChunks(chunks: List[Chunk]): EitherT[IO, String, List[Embedding]] =
    embedBatch(
      chunks.map(_.content),
      "passage"
    )

  def embedBatch(
      texts: List[String],
      instruction: String
  ): EitherT[IO, String, List[Embedding]] =
    val partitionCount = texts.length / embedPartitionSize
    logger.info("Using GridoAIML")
    logger.info(
      s"Trying to get ${texts.length} embeddings divided into $partitionCount partitions and sending groups of $embedParallelism partitions"
    )
    executeByPartsInParallel(
      embedPartitionOfTexts(instruction),
      embedPartitionSize,
      embedParallelism
    )(texts)

  def embedPartitionOfTexts(instruction: String)(
      texts: List[String]
  ): EitherT[IO, String, List[Embedding]] =
    logger.info(
      s"Sending partition of ${texts.length} texts"
    )
    val body = GridoAIMLEmbeddingRequest(texts, instruction).asJson.noSpaces
    HttpBatch
      .post("")
      .headers(Map("Content-Type" -> "application/json"))
      .body(body)
      .sendReq()
      .map: response =>
        logger.info(s"Partition of ${texts.length} texts received.")
        response.body.flatMap(
          decode[MessageResponse[List[List[Float]]]](_).left.map(_.getMessage())
        )
      .timeoutTo(
        5.minutes,
        IO.pure(Left("GridoAI ML API timed out after 5 minutes"))
      )
      .asEitherT
      .map(
        _.message.map(vec =>
          Embedding(vector = vec, model = EmbeddingModel.MultilingualE5Base)
        )
      )
      .attempt

  def embed(
      texts: List[String],
      instruction: String
  ): EitherT[IO, String, List[Embedding]] = traceMappable("embed"):
    val body =
      GridoAIMLEmbeddingRequest(texts, instruction).asJson.noSpaces
    HttpSingle
      .post("")
      .headers(Map("Content-Type" -> "application/json"))
      .body(body)
      .sendReq()
      .map: response =>
        response.body.flatMap(
          decode[MessageResponse[List[List[Float]]]](_).left.map(_.getMessage())
        )
      .timeoutTo(
        15.seconds,
        IO.pure(Left("GridoAI ML API timed out after 15 seconds"))
      )
      .asEitherT
      .map(
        _.message.map(v =>
          Embedding(
            vector = v,
            model = EmbeddingModel.MultilingualE5Base
          )
        )
      )
      .attempt
