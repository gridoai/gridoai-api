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
val embedParallelism =
  sys.env.getOrElse("EMBEDDING_API_PARALLELISM", "4").toInt
val embedPartitionSize =
  sys.env.getOrElse("EMBEDDING_API_PARTITION_SIZE", "64").toInt

val _ = println(
  s"embedParallelism: $embedParallelism \n embedPartitionSize: $embedPartitionSize \n embeddingApiEndpoint: $embeddingApiEndpoint"
)
object GridoAIML extends EmbeddingAPI[IO]:
  val logger = LoggerFactory.getLogger(getClass.getName)
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
    Http
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
    embedMany(List(text), instruction).map(_.map(_.head))
