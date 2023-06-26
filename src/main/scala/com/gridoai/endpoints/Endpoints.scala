package com.gridoai.endpoints
import com.gridoai.domain.*
import io.circe.generic.auto._
import sttp.model.Part
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import com.gridoai.parsers.FileFormats
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.apispec.openapi.circe.yaml._
import java.io.File
import java.util.UUID

type PublicEndpoint[I, E, O, -R] = Endpoint[Unit, I, E, O, R]

case class FileUpload(files: List[Part[File]])

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

val getSchema =
  OpenAPIDocsInterpreter().toOpenAPI(searchEndpoint, "GridoAI", "1.0").toYaml

def dumpSchema() =
  import java.io._
  val bw = BufferedWriter(FileWriter(File("openapi.yaml")))
  bw.write(getSchema)
  bw.close()
