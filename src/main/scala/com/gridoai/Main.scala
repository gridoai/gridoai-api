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
import com.gridoai.adapters.notifications.AblyNotificationService
import com.gridoai.adapters.notifications.NotificationService
import com.gridoai.utils.LRUCache

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    if args get 0 contains "openapi" then
      endpoints.dumpSchema()
      IO.pure(ExitCode.Success)
    else
      PostgresClient
        .getTransactor[IO]
        .use: transactor =>
          given docDb: DocDB[IO] = PostgresClient[IO](transactor)
          given ns: NotificationService[IO] = AblyNotificationService[IO]
          given lruCache: LRUCache[String, Unit] =
            LRUCache[String, Unit](10)

          endpoints.http4s.runHttp4s

}
