package com.gridoai.endpoints
import cats.effect.IO
import com.gridoai.domain.*
import com.gridoai.models.DocDB
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

import java.util.UUID
type PublicEndpoint[I, E, O, -R] = Endpoint[Unit, I, E, O, R]

val searchEndpoint: PublicEndpoint[String, String, List[Document], Any] =
  endpoint
    .name("Search")
    .description("Search for documents in the knowledge base")
    .in("search")
    .in(query[String]("query"))
    .out(jsonBody[List[Document]])
    .errorOut(stringBody)

val healthCheckEndpoint: PublicEndpoint[Unit, Unit, String, Any] =
  endpoint
    .name("Health Check")
    .description("Check if the service is up")
    .in("health")
    .out(stringBody)

val createDocumentEndpoint
    : PublicEndpoint[DocCreationPayload, String, Unit, Any] =
  endpoint
    .name("Create Document")
    .description("Create a document in the knowledge base")
    .post
    .in("document")
    .in(jsonBody[DocCreationPayload])
    .out(emptyOutput)
    .errorOut(stringBody)

val askEndpoint: PublicEndpoint[List[Message], String, String, Any] =
  endpoint
    .name("Ask to LLM")
    .description("Ask something based on knowledge base")
    .post
    .in("ask")
    .in(jsonBody[List[Message]])
    .out(stringBody)
    .errorOut(stringBody)