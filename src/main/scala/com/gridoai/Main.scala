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
import cats.data.EitherT
import me.shadaj.scalapy.py
import me.shadaj.scalapy.py.SeqConverters
import scala.util.Try
import fs2.interop.reactivestreams._
import com.gridoai.sk.BaseKernel
import com.gridoai.adapters.llm.chatGPT.ChatGPTClient.client

case class TranscriptItem(text: String, start: Double, duration: Double)

object Main extends IOApp:

  def run(args: List[String]) =
    adapters.l4j.run
    // if args get 0 contains "openapi" then endpoints.dumpSchema()
    // val videoId = "d6jKdrzal0g"
    // val mod = py.module("youtube_transcript_api")
    // val x = (
    //   mod.YouTubeTranscriptApi
    //     .get_transcript(videoId, Seq("pt", "en").toPythonProxy)
    // )
    // println(
    //   x.as[Seq[py.Dynamic]]
    //     .map { item =>
    //       TranscriptItem(
    //         text = item.bracketAccess("text").as[String],
    //         start = item.bracketAccess("start").as[Double],
    //         duration = item.bracketAccess("duration").as[Double]
    //       )
    //     }
    //     .map(_.text)
    //     .mkString("\n")
    // )
    // IO.pure(ExitCode.Success)
    // else
    // import com.azure.ai.openai.models.ChatMessage
    // Try(
    //   com.gridoai.sk.Example99_BlogAnnouncement.main(
    //     BaseKernel.get(
    //       client,
    //       "command-nightly",
    //       "command-nightly"
    //     )
    //   )
    // )
    // .kernel
    // .runAsync("redact the password password.12344")
    // .block()

    IO.pure(ExitCode.Success)
    // (for
    //   transactor <- PostgresClient.getTransactor[IO]
    //   redis <- RedisClient.getRedis[IO]
    // yield (transactor, redis)).use: (transactor, redis) =>
    //   given docDb: DocDB[IO] = PostgresClient[IO](transactor)
    //   given messageDb: MessageDB[IO] = RedisClient[IO](redis)
    //   given ns: NotificationService[IO] = AblyNotificationService[IO]
    //   given emailApi: EmailAPI[IO] = ResendClient[IO]
    //   endpoints.http4s.runHttp4s
