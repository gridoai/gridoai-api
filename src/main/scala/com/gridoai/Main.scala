package com.gridoai
import cats.effect.*
import cats.effect.unsafe.implicits.global
import cats.implicits.*
import com.comcast.ip4s.ipv4
import com.comcast.ip4s.port
import com.google.cloud.functions.HttpFunction
import com.google.cloud.functions.HttpRequest
import com.google.cloud.functions.HttpResponse
import com.gridoai.adapters.Neo4jAsync
import com.gridoai.domain.Document
import com.gridoai.domain.Mentions
import com.gridoai.endpoints.*
import com.gridoai.endpoints.*
import com.gridoai.endpoints.getSchema
import com.gridoai.models.DocDB
import com.gridoai.models.MockDocDB
import com.gridoai.models.Neo4j
import de.killaitis.http4s.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.syntax.*
import org.http4s.ember.server.EmberServerBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class ScalaHttpFunction extends HttpFunction {
  def service(request: HttpRequest, response: HttpResponse) =

    given docDb: DocDB[IO] = MockDocDB
    (Http4sCloudFunction(httpApp).service(request, response))

}

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    if args get 0 contains "openapi" then
      import java.io._
      val bw = BufferedWriter(FileWriter(File("openapi.yaml")))
      bw.write(getSchema)
      bw.close()
      IO.pure(ExitCode.Success)
    else

      given docDb: DocDB[IO] = MockDocDB

      EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8080")
        .withHttpApp(httpApp)
        .build
        .use(_ => IO.never)
        .as(ExitCode.Success)

}
