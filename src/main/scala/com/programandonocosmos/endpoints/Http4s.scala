package com.programandonocosmos.endpoints

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.catsSyntaxApplicativeId
import cats.implicits.toSemigroupKOps
import com.programandonocosmos.adapters.Neo4jAsync
import com.programandonocosmos.adapters.contextHandler.*
import com.programandonocosmos.domain.Document
import com.programandonocosmos.models.DocDB
import com.programandonocosmos.models.Neo4j
import com.programandonocosmos.services.doc.*
import io.circe.DecodingFailure
import org.http4s.HttpApp
import org.http4s.HttpRoutes
import org.http4s.server.Router
import org.http4s.server.middleware.CORS
import sttp.tapir._
import sttp.tapir.server.http4s.Http4sServerInterpreter

import java.util.UUID

import util.chaining.scalaUtilChainingOps
import concurrent.ExecutionContext.Implicits.global

val aiPluginService =
  import cats.effect._
  import org.http4s._
  import org.http4s.dsl.io._
  import org.http4s.implicits._
  import org.http4s.server.staticcontent.resourceServiceBuilder
  import concurrent.ExecutionContext.Implicits.global

  HttpRoutes.of[IO] {
    case request @ GET -> Root / ".well-known" / "ai-plugin.json" =>
      StaticFile
        .fromResource(
          "./assets/ai-plugin.json",
          Some(request)
        )
        .getOrElseF(NotFound())
    case request @ GET -> Root / "openapi.yaml" =>
      println("a")
      StaticFile
        .fromResource("./assets/openapi.yaml", Some(request))
        .getOrElseF(NotFound())
  }

def searchEndpointGet(implicit db: DocDB[IO]): HttpRoutes[IO] =
  Http4sServerInterpreter[IO]().toRoutes(
    searchEndpoint.serverLogic(searchDoc _)
  )

def healthCheckEndpointGet: HttpRoutes[IO] =
  Http4sServerInterpreter[IO]().toRoutes(
    healthCheckEndpoint.serverLogic(_ => IO.pure(Right("OK")))
  )

def createEndpoint(implicit db: DocDB[IO]): HttpRoutes[IO] =
  Http4sServerInterpreter[IO]().toRoutes(
    createDocumentEndpoint.serverLogic(createDoc _)
  )

def uploadFileEndpoint(implicit db: DocDB[IO]): HttpRoutes[IO] =
  Http4sServerInterpreter[IO]().toRoutes(
    fileUploadEndpoint.serverLogic(files =>
      println(files)
      uploadDocuments(files).map(Right(_))
    )
  )

def endpoints(implicit db: DocDB[IO]): HttpRoutes[IO] =
  searchEndpointGet <+> healthCheckEndpointGet <+> createEndpoint <+> uploadFileEndpoint

def HttpApp(implicit db: DocDB[IO]): HttpApp[IO] =
  Router(
    "/" -> CORS.policy.withAllowOriginAll(endpoints)
  ).orNotFound
