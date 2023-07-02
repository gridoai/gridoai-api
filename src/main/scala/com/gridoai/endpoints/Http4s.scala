package com.gridoai.endpoints.http4s

import cats.effect.IO
import com.gridoai.endpoints
import com.gridoai.models.DocDB

import org.http4s.HttpApp
import org.http4s.HttpRoutes
import org.http4s.server.Router
import org.http4s.server.middleware.CORS
import org.http4s.server.middleware.ErrorAction
import org.http4s.server.middleware.ErrorHandling
import sttp.tapir._
import sttp.tapir.server.http4s.Http4sServerInterpreter

def routes(implicit db: DocDB[IO]): HttpRoutes[IO] =
  Http4sServerInterpreter[IO]().toRoutes(endpoints.withService.allEndpoints)

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
