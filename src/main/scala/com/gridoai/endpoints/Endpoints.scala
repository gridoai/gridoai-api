package com.gridoai.endpoints
import cats.effect.IO
import com.gridoai.domain.*
import com.gridoai.endpoints.auth
import com.gridoai.parsers.FileFormat
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
import com.gridoai.adapters.*
import com.gridoai.adapters.whatsapp.Whatsapp

type PublicEndpoint[I, E, O, -R] = Endpoint[Unit, I, E, O, R]
type SecuredEndpoint[I, E, O, -R] =
  PartialServerEndpoint[String, AuthData, I, E, O, R, IO]

case class FileUpload(files: List[Part[File]])

enum FileUploadError:
  case FileParseError(format: FileFormat, m: String)
  case DocumentCreationError(m: String)
  case UnknownError(m: String)
  case UnauthorizedError(m: String)

val fileUploadEndpoint: SecuredEndpoint[FileUpload, List[
  Either[FileUploadError, String]
] | String, Unit, Any] =
  auth.securedWithBearer.post
    .name("File Upload")
    .in("upload")
    .in(multipartBody[FileUpload])
    .out(jsonBody[Unit])
    .mapErrorOut(identity)(_.toString())

val billingSession: SecuredEndpoint[Option[String], String, String, Any] =
  auth.securedWithBearer.post
    .name("Billing session")
    .in("billing" / "session")
    .in(header[Option[String]]("Origin"))
    .out(stringBody)

val webhooksStripe: PublicEndpoint[(String, String), String, String, Any] =
  endpoint
    .name("Stripe Webhook")
    .in("webhooks" / "stripe")
    .in(stringBody)
    .in(header[String]("Stripe-Signature"))
    .out(stringBody)
    .errorOut(stringBody)

val webhooksWhatsappChallenge
    : PublicEndpoint[(String, String), String, String, Any] =
  endpoint.get
    .name("WhatsApp Challenge")
    .in("webhooks" / "whatsapp")
    .in(query[String]("hub.verify_token"))
    .in(query[String]("hub.challenge"))
    .out(stringBody)
    .errorOut(stringBody)

val webhooksWhatsapp
    : PublicEndpoint[Whatsapp.WebhookPayload, String, Unit, Any] =
  endpoint.post
    .name("WhatsApp Webhook")
    .in("webhooks" / "whatsapp")
    .in(jsonBody[Whatsapp.WebhookPayload])
    .out(emptyOutput)
    .errorOut(stringBody)

val notificationAuthEndpoint: SecuredEndpoint[Unit, String, String, Any] =
  auth.securedWithBearer
    .name("Notification Auth")
    .description("Authenticate client to access notification service")
    .in("notifications")
    .in("auth")
    .out(stringBody)

val listEndpoint
    : SecuredEndpoint[(Int, Int, Boolean), String, PaginatedResponse[
      List[Document]
    ], Any] =
  auth.securedWithBearer
    .name("List")
    .description("List all documents in the knowledge base")
    .in("documents")
    .in(query[Option[Int]]("start").map(_.getOrElse(0))(Some(_)))
    .in(query[Option[Int]]("end").map(_.getOrElse(10))(Some(_)))
    .in(query[Option[Boolean]]("truncate").map(_.getOrElse(true))(Some(_)))
    .out(jsonBody[PaginatedResponse[List[Document]]])

val deleteEndpoint: SecuredEndpoint[String, String, Unit, Any] =
  auth.securedWithBearer
    .name("Delete")
    .description("Delete a document from the knowledge base")
    .in("documents")
    .in(path[String]("id"))
    .out(emptyOutput)

val searchEndpoint: SecuredEndpoint[SearchPayload, String, List[
  Chunk
], Any] =
  auth.securedWithBearer
    .name("Search")
    .description("Search for documents in the knowledge base")
    .post
    .in("search")
    .in(jsonBody[SearchPayload])
    .out(jsonBody[List[Chunk]])

val healthCheckEndpoint: PublicEndpoint[Unit, Unit, String, Any] =
  endpoint
    .name("Health Check")
    .description("Check if the service is up")
    .in("health")
    .out(stringBody)

val createDocumentEndpoint
    : SecuredEndpoint[DocumentCreationPayload, String, String, Any] =
  auth.securedWithBearer
    .name("Create Document")
    .description("Create a document in the knowledge base")
    .post
    .in("documents")
    .in(jsonBody[DocumentCreationPayload])
    .out(stringBody)

val authGDriveEndpoint
    : SecuredEndpoint[(String, String), String, (String, String), Any] =
  auth.securedWithBearer
    .name("Access for Google Drive")
    .description("Authenticate backend to access Google Drive")
    .in("gdrive")
    .in("auth")
    .in(query[String]("code"))
    .in(query[String]("redirectUri"))
    .out(jsonBody[(String, String)])

val importGDriveEndpoint: SecuredEndpoint[List[String], String, Unit, Any] =
  auth.securedWithBearer
    .name("Import data from Google Drive")
    .description("Import google drive documents in the knowledge base")
    .post
    .in("gdrive")
    .in("import")
    .in(jsonBody[List[String]])
    .out(jsonBody[Unit])

val askEndpoint: SecuredEndpoint[AskPayload, String, AskResponse, Any] =
  auth.securedWithBearer
    .name("Ask to LLM")
    .description("Ask something based on knowledge base")
    .post
    .in("ask")
    .in(jsonBody[AskPayload])
    .out(jsonBody[AskResponse])

case class RefreshTokenPayload(refreshToken: String)

val refreshGDriveTokenEndpoint
    : SecuredEndpoint[RefreshTokenPayload, String, (String, String), Any] =
  auth.securedWithBearer
    .name("Refresh Google Drive Token")
    .description("Refresh Google Drive Token")
    .post
    .in("gdrive")
    .in("refresh")
    .in(jsonBody[RefreshTokenPayload])
    .out(jsonBody[(String, String)])

val allEndpoints: List[AnyEndpoint] =
  List(
    fileUploadEndpoint.endpoint,
    listEndpoint.endpoint,
    deleteEndpoint.endpoint,
    searchEndpoint.endpoint,
    healthCheckEndpoint,
    createDocumentEndpoint.endpoint,
    authGDriveEndpoint.endpoint,
    importGDriveEndpoint.endpoint,
    askEndpoint.endpoint,
    webhooksStripe,
    webhooksWhatsappChallenge,
    webhooksWhatsapp,
    refreshGDriveTokenEndpoint.endpoint,
    billingSession.endpoint
  )

val getSchema =
  OpenAPIDocsInterpreter()
    .toOpenAPI(allEndpoints, "GridoAI", "1.0")
    .toYaml

def dumpSchema() =
  import java.io._
  val bw = BufferedWriter(FileWriter(File("openapi.yaml")))
  bw.write(getSchema)
  bw.close()
