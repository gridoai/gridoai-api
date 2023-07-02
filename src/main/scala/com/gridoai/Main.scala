package com.gridoai
import cats.effect.*
import cats.effect.unsafe.implicits.global
import cats.implicits.*
import com.comcast.ip4s.ipv4
import com.comcast.ip4s.port
import com.google.cloud.functions.HttpFunction
import com.google.cloud.functions.HttpRequest
import com.google.cloud.functions.HttpResponse
import com.gridoai.models.PostgresClient
import com.gridoai.models.DocDB
import de.killaitis.http4s.*

import org.http4s.ember.server.EmberServerBuilder

class ScalaHttpFunction extends HttpFunction {
  def service(request: HttpRequest, response: HttpResponse) =

    given docDb: DocDB[IO] = PostgresClient
    (Http4sCloudFunction(endpoints.http4s.httpApp).service(request, response))

}

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    if args get 0 contains "openapi" then
      endpoints.dumpSchema()
      IO.pure(ExitCode.Success)
    else

      given docDb: DocDB[IO] = PostgresClient

      EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8080")
        .withHttpApp(endpoints.http4s.httpApp)
        .build
        .use(_ => IO.never)
        .as(ExitCode.Success)

}
