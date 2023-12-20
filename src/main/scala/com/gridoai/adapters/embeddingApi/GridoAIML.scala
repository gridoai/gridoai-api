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
import org.slf4j.LoggerFactory

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
    model: String = "multilingual-e5-base"
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

  def embedChat(text: String): IO[Either[String, Embedding]] =
    embed(
      text,
      "query"
    )
  def embedChunks(chunks: List[Chunk]): IO[Either[String, List[Embedding]]] =
    embedBatch(
      chunks.map(_.content),
      "passage"
    )

  def embedBatch(
      texts: List[String],
      instruction: String
  ): IO[Either[String, List[Embedding]]] =
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
  ): IO[Either[String, List[Embedding]]] =
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
      .mapRight(
        _.message.map(vec =>
          Embedding(vector = vec, model = EmbeddingModel.MultilingualE5Base)
        )
      ) |> attempt

  def embed(
      text: String,
      instruction: String
  ): IO[Either[String, Embedding]] = traceMappable("embed"):
    val body =
      GridoAIMLEmbeddingRequest(List(text), instruction).asJson.noSpaces
    HttpSingle
      .post("")
      .headers(Map("Content-Type" -> "application/json"))
      .body(body)
      .sendReq()
      .map: response =>
        response.body.flatMap(
          decode[MessageResponse[List[List[Float]]]](_).left.map(_.getMessage())
        )
      .mapRight(r =>
        Embedding(
          vector = r.message.head,
          model = EmbeddingModel.MultilingualE5Base
        )
      ) |> attempt
