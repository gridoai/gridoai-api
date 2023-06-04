package com.programandonocosmos.endpoints
import cats.effect.IO
import com.programandonocosmos.domain.*
import com.programandonocosmos.models.DocDB
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

val createDocumentEndpoint: PublicEndpoint[Document, String, Unit, Any] =
  endpoint
    .name("Create Document")
    .description("Create a document to the knowledge base")
    .post
    .in("document")
    .in(jsonBody[Document])
    .out(emptyOutput)
    .errorOut(stringBody)
