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
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter
import org.http4s.ember.server.EmberServerBuilder
import com.comcast.ip4s.ipv4
import cats.effect.ExitCode
import com.comcast.ip4s.Port
import com.comcast.ip4s.port
import com.gridoai.utils.getEnv
import cats.effect.kernel.Sync
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import com.gridoai.adapters.notifications.NotificationService
import com.gridoai.utils.LRUCache
import com.gridoai.domain.WhatsAppMessage

def routes(implicit
    db: DocDB[IO],
    ns: NotificationService[IO],
    lruCache: LRUCache[String, List[WhatsAppMessage]]
): HttpRoutes[IO] =
  Http4sServerInterpreter[IO]().toRoutes(endpoints.withService().allEndpoints)

def httpApp(implicit
    db: DocDB[IO],
    ns: NotificationService[IO],
    lruCache: LRUCache[String, List[WhatsAppMessage]]
): HttpApp[IO] =
  Router(
    "/" -> CORS.policy.withAllowOriginAll(routes)
  ).orNotFound

def http4sAppBuilder(implicit
    db: DocDB[IO],
    ns: NotificationService[IO],
    lruCache: LRUCache[String, List[WhatsAppMessage]]
) =
  EmberServerBuilder
    .default[IO]
    .withLogger(Slf4jLogger.getLogger[IO])
    .withHost(ipv4"0.0.0.0")
    .withHttp2
    .withPort(
      sys.env.get("PORT").flatMap(Port.fromString).getOrElse(port"8080")
    )
    .withHttpApp(httpApp)

def runHttp4s(implicit
    db: DocDB[IO],
    ns: NotificationService[IO],
    lruCache: LRUCache[String, List[WhatsAppMessage]]
) =
  http4sAppBuilder.build
    .use(_ => IO.never)
    .as(ExitCode.Success)
