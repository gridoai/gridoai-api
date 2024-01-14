package com.gridoai.endpoints

import com.gridoai.models.DocDB
import com.gridoai.models.MessageDB
import sttp.tapir._
import sttp.tapir.server.vertx.cats.VertxCatsServerOptions
import cats.effect._
import cats.effect.std.Dispatcher
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.vertx.cats.VertxCatsServerInterpreter
import sttp.tapir.server.vertx.cats.VertxCatsServerInterpreter._
import sttp.tapir.server.interceptor.cors.{CORSInterceptor, CORSConfig}
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.interceptor.cors.CORSConfig.AllowedCredentials.Allow
import sttp.tapir.server.interceptor.cors.CORSConfig.AllowedOrigin
import sttp.tapir.server.interceptor.cors.CORSConfig.AllowedHeaders
import sttp.tapir.server.interceptor.cors.CORSConfig.AllowedMethods
import sttp.tapir.server.interceptor.cors.CORSConfig.ExposedHeaders
import sttp.model.StatusCode
import io.vertx.core.http.HttpServerOptions
import sttp.model.Method
import com.gridoai.adapters.notifications.NotificationService
import com.gridoai.adapters.emailApi.EmailAPI

def runVertxWithEndpoint(
    endpoints: List[ServerEndpoint[Fs2Streams[IO], cats.effect.IO]]
) =
  val port = sys.env.get("HTTP_PORT").flatMap(_.toIntOption).getOrElse(8080)

  Dispatcher
    .parallel[IO]
    .map(d => {
      VertxCatsServerOptions
        .customiseInterceptors[IO](d)
        .corsInterceptor(
          CORSInterceptor.customOrThrow(
            CORSConfig.default.copy(
              allowedCredentials = Allow,
              allowedOrigin = AllowedOrigin.Matching(_ => true),
              allowedMethods = AllowedMethods.Some(
                Set(
                  Method.POST,
                  Method.GET,
                  Method.OPTIONS,
                  Method.DELETE,
                  Method.PUT
                )
              ),
              allowedHeaders = AllowedHeaders.Some(
                Set(
                  "Content-Type",
                  "Authorization",
                  "Access-Control-Allow-Origin",
                  "Access-Control-Allow-Headers",
                  "Access-Control-Allow-Methods",
                  "Access-Control-Allow-Credentials"
                )
              )
            )
          )
        )
        .options
    })
    .flatMap { dispatcher =>
      Resource
        .make(
          IO.delay {
            val opts = HttpServerOptions().setUseAlpn(true)
            val vertx = Vertx.vertx()
            val server = vertx.createHttpServer(opts)
            val router = Router.router(vertx)

            endpoints.foreach(endpoint => {
              VertxCatsServerInterpreter[IO](dispatcher)
                .route(endpoint)
                .apply(router)
            })
            server.requestHandler(router).listen(port)
          }.flatMap(_.asF[IO])
        )({ server =>
          IO.delay(server.close).flatMap(_.asF[IO].void)
        })
    }
    .use(_ => IO.never)
    .as(ExitCode.Success)

def runVertex(implicit
    db: DocDB[IO],
    ns: NotificationService[IO],
    messageDb: MessageDB[IO],
    emailApi: EmailAPI[IO]
) =
  runVertxWithEndpoint(withService().allEndpoints)
