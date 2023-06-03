package com.programandonocosmos
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.*
import cats.effect.unsafe.implicits.global
import cats.implicits.*
import com.comcast.ip4s.ipv4
import com.comcast.ip4s.port
import com.google.cloud.functions.HttpFunction
import com.google.cloud.functions.HttpRequest
import com.google.cloud.functions.HttpResponse
import com.programandonocosmos.adapters.Neo4jAsync
import com.programandonocosmos.domain.Document
import com.programandonocosmos.domain.Mentions
import com.programandonocosmos.endpoints.*
import com.programandonocosmos.models.DocDB
import com.programandonocosmos.models.Neo4j
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
  // Lazy-initialized resource for the Neo4j connection
  private lazy val neo4jResource =
    Neo4jAsync.resourceWithCredentials

  // Create the Neo4j runner once and reuse it for each function call
  private val neo4jRunner = neo4jResource.use(IO.pure).unsafeRunSync()

  def service(request: HttpRequest, response: HttpResponse): Unit =
    given docDb: DocDB[IO] = Neo4j(neo4jRunner)

    // Handle the request using the shared Neo4j connection
    val httpService =
      Http4sCloudFunction(HttpApp).service(request, response)
}

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    Neo4jAsync.resourceWithCredentials.use { runner =>
      given docDb: DocDB[IO] = Neo4j(runner)
      EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8080")
        .withHttpApp(HttpApp)
        .build
        .use(_ => IO.never)
        .as(ExitCode.Success)

    }
}
