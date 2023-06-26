package com.gridoai.endpoints

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.catsSyntaxApplicativeId
import cats.implicits.toSemigroupKOps
import com.gridoai.adapters.Neo4jAsync
import com.gridoai.adapters.contextHandler.*
import com.gridoai.domain.Document
import com.gridoai.models.DocDB
import com.gridoai.models.Neo4j
import com.gridoai.services.doc.*
import io.circe.DecodingFailure
import org.http4s.HttpApp
import org.http4s.HttpRoutes
import org.http4s.server.Router
import org.http4s.server.middleware.CORS
import org.http4s.server.middleware.ErrorAction
import org.http4s.server.middleware.ErrorHandling
import sttp.tapir._
import sttp.tapir.server.http4s.Http4sServerInterpreter

import java.util.UUID

import util.chaining.scalaUtilChainingOps
import concurrent.ExecutionContext.Implicits.global

def searchRoute(implicit db: DocDB[IO]): HttpRoutes[IO] =
  Http4sServerInterpreter[IO]().toRoutes(
    searchEndpoint.serverLogic(searchDoc _)
  )

def healthCheckRoute: HttpRoutes[IO] =
  Http4sServerInterpreter[IO]().toRoutes(
    healthCheckEndpoint.serverLogic(_ => IO.pure(Right("OK")))
  )

def createRoute(implicit db: DocDB[IO]): HttpRoutes[IO] =
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

def askRoute(implicit db: DocDB[IO]): HttpRoutes[IO] =
  Http4sServerInterpreter[IO]().toRoutes(
    askEndpoint.serverLogic(ask _)
  )

def routes(implicit db: DocDB[IO]): HttpRoutes[IO] =
  searchRoute <+> healthCheckRoute <+> createRoute <+> askRoute <+> uploadFileEndpoint

def httpApp(implicit db: DocDB[IO]): HttpApp[IO] =
  ErrorHandling.Recover.total(
    ErrorAction.log(
      Router(
        "/" -> CORS.policy.withAllowOriginAll(routes)
      ).orNotFound,
      messageFailureLogAction = (t, msg) =>
        IO.println(msg) >>
          IO.println(t),
      serviceErrorLogAction = (t, msg) =>
        IO.println(msg) >>
          IO.println(t)
    )
  )
