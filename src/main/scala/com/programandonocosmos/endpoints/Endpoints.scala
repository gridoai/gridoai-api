package com.programandonocosmos.endpoints
import cats.effect.IO
import com.programandonocosmos.domain.*
import com.programandonocosmos.models.DocDB
import io.circe.generic.auto._
import sttp.model.Part
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

import java.io.File
import java.util.UUID

type PublicEndpoint[I, E, O, -R] = Endpoint[Unit, I, E, O, R]

case class FileUpload(files: List[Part[File]])
enum FileFormats:
  case PDF, PPTX, DOCX

enum FileUploadError:
  case FileParseError(format: FileFormats, m: String)
  case DocumentCreationError(m: String)
  case UnknownError(m: String)

val fileUploadEndpoint: PublicEndpoint[FileUpload, String, List[
  Either[FileUploadError, Unit]
], Any] =
  endpoint.post
    .in("upload")
    .in(multipartBody[FileUpload])
    .errorOut(stringBody)
    .out(jsonBody[List[Either[FileUploadError, Unit]]])

val searchEndpoint =
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

val getSchema =
  import sttp.apispec.openapi.OpenAPI
  import sttp.tapir._
  import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
  import sttp.apispec.openapi.circe.yaml._

  OpenAPIDocsInterpreter().toOpenAPI(searchEndpoint, "GridoAI", "1.0").toYaml