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
import com.gridoai.models.MessageDB
import com.gridoai.models.RedisClient
import de.killaitis.http4s.*

import org.http4s.ember.server.EmberServerBuilder
import com.gridoai.adapters.notifications.AblyNotificationService
import com.gridoai.adapters.notifications.NotificationService
import com.gridoai.adapters.emailApi.EmailAPI
import com.gridoai.adapters.emailApi.ResendClient

object Main extends IOApp:
  def run(args: List[String]) =
    if args get 0 contains "openapi" then
      endpoints.dumpSchema()
      IO.pure(ExitCode.Success)
    else
      (for
        transactor <- PostgresClient.getTransactor[IO]
        redis <- RedisClient.getRedis[IO]
      yield (transactor, redis)).use: (transactor, redis) =>
        given docDb: DocDB[IO] = PostgresClient[IO](transactor)
        given messageDb: MessageDB[IO] = RedisClient[IO](redis)
        given ns: NotificationService[IO] = AblyNotificationService[IO]
        given emailApi: EmailAPI[IO] = ResendClient[IO]
        endpoints.http4s.runHttp4s
