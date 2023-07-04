package com.gridoai.endpoints
import cats.effect.IO
import com.gridoai.domain.*
import com.gridoai.endpoints.auth
import com.gridoai.parsers.FileFormats
import io.circe.generic.auto._
import sttp.apispec.openapi.circe.yaml._
import sttp.model.Part
import sttp.tapir._
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.PartialServerEndpoint

import java.io.File
import java.util.UUID
import com.gridoai.auth.AuthData

type PublicEndpoint[I, E, O, -R] = Endpoint[Unit, I, E, O, R]
type SecuredEndpoint[I, E, O, -R] =
  PartialServerEndpoint[String, AuthData, I, E, O, R, IO]

case class FileUpload(files: List[Part[File]])

enum FileUploadError:
  case FileParseError(format: FileFormats, m: String)
  case DocumentCreationError(m: String)
  case UnknownError(m: String)
  case UnauthorizedError(m: String)

val fileUploadEndpoint
    : SecuredEndpoint[FileUpload, List[FileUploadError] | String, Unit, Any] =
  auth.securedWithBearer.post
    .in("upload")
    .in(multipartBody[FileUpload])
    .out(jsonBody[Unit])
    .mapErrorOut(identity)(_.toString())

val searchEndpoint: SecuredEndpoint[String, String, List[Document], Any] =
  auth.securedWithBearer
    .name("Search")
    .description("Search for documents in the knowledge base")
    .in("search")
    .in(query[String]("query"))
    .out(jsonBody[List[Document]])

val healthCheckEndpoint: PublicEndpoint[Unit, Unit, String, Any] =
  endpoint
    .name("Health Check")
    .description("Check if the service is up")
    .in("health")
    .out(stringBody)

val createDocumentEndpoint
    : SecuredEndpoint[DocumentCreationPayload, String, Unit, Any] =
  auth.securedWithBearer
    .name("Create Document")
    .description("Create a document in the knowledge base")
    .post
    .in("document")
    .in(jsonBody[DocumentCreationPayload])
    .out(emptyOutput)

val askEndpoint: SecuredEndpoint[List[Message], String, String, Any] =
  auth.securedWithBearer
    .name("Ask to LLM")
    .description("Ask something based on knowledge base")
    .post
    .in("ask")
    .in(jsonBody[List[Message]])
    .out(stringBody)

val getSchema =
  OpenAPIDocsInterpreter()
    .toOpenAPI(askEndpoint.endpoint, "GridoAI", "1.0")
    .toYaml

def dumpSchema() =
  import java.io._
  val bw = BufferedWriter(FileWriter(File("openapi.yaml")))
  bw.write(getSchema)
  bw.close()
