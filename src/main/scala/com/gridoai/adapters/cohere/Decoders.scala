package com.gridoai.adapters.cohere

import scala.util.Try
import io.circe.syntax._
import io.circe._
import cats.syntax.functor._

// For serialising string unions
given [A <: Singleton](using A <:< String): Decoder[A] =
  Decoder.decodeString.emapTry(x => Try(x.asInstanceOf[A]))
given [A <: Singleton](using ev: A <:< String): Encoder[A] =
  Encoder.encodeString.contramap(ev)

// If a union has a null in, then we'll need this too...
type Nullue = None.type

case class CohereLLMResponse(
    response_id: String,
    text: String,
    generation_id: String,
    finish_reason: String,
    token_count: TokenCount,
    meta: Meta,
    citations: Seq[Citation],
    documents: Seq[Document],
    search_results: Seq[SearchResult],
    search_queries: Seq[SearchQuery]
) derives Encoder.AsObject,
      Decoder

case class Citation(
    start: Long,
    end: Long,
    text: String,
    document_ids: Seq[String]
) derives Encoder.AsObject,
      Decoder

case class Document(
    id: String,
    snippet: String,
    timestamp: String,
    title: String,
    url: String
) derives Encoder.AsObject,
      Decoder

case class Meta(
    api_version: APIVersion,
    billed_units: BilledUnits
) derives Encoder.AsObject,
      Decoder

case class APIVersion(
    version: String
) derives Encoder.AsObject,
      Decoder

case class BilledUnits(
    input_tokens: Long,
    output_tokens: Long
) derives Encoder.AsObject,
      Decoder

case class SearchQuery(
    text: String,
    generation_id: String
) derives Encoder.AsObject,
      Decoder

case class SearchResult(
    search_query: SearchQuery,
    document_ids: Seq[String],
    connector: Connector
) derives Encoder.AsObject,
      Decoder

case class Connector(
    id: String
) derives Encoder.AsObject,
      Decoder

case class TokenCount(
    prompt_tokens: Long,
    response_tokens: Long,
    total_tokens: Long,
    billed_tokens: Long
) derives Encoder.AsObject,
      Decoder

case class Payload(
    model: String = "command-nightly",
    message: String,
    temperature: Double = 0.3,
    chat_history: Seq[Option[Json]] = Seq.empty,
    prompt_truncation: String = "auto",
    stream: Boolean = false,
    citation_quality: String = "accurate",
    connectors: Seq[Connector] = Seq(Connector("web-search")),
    documents: Seq[Option[Json]] = Seq.empty
) derives Encoder.AsObject,
      Decoder
