package com.gridoai.adapters.notifications
import cats.effect.IO

import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.Channel
import scala.concurrent.Future
import scala.util.{Try, Success, Failure}
import cats.effect.kernel.Async
import cats.effect.kernel.Sync
import io.ably.lib.rest.Auth.TokenParams
import io.ably.lib.rest.Auth.AuthOptions
import io.ably.lib.rest.AblyRest
import io.ably.lib.realtime.CompletionListener
import io.ably.lib.types.ErrorInfo
val ablyClient = AblyRest(sys.env("ABLY_KEY"))

class AblyNotificationService[F[_]: Async]() extends NotificationService[F]:
  val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  def sendNotification(
      topic: String,
      channelName: String,
      content: String
  ): F[Either[String, Unit]] =
    val channel = ablyClient.channels.get(channelName)
    Async[F].async_ : callback =>
      try
        channel.publishAsync(
          topic,
          content,
          new CompletionListener():
            override def onSuccess(): Unit =
              logger.info(s"Notification sent to $channelName")
              callback(Right(Right(())))

            override def onError(reason: ErrorInfo): Unit =
              logger.error(
                s"Notification failed to send to $channelName: ${reason.message}"
              )
              callback(Left(new Exception(reason.message)))
        )
      catch case ex => Left(ex.getMessage)

def generateToken[F[_]: Sync](clientId: String) =
  Sync[F].blocking:
    try
      val tokenParams = TokenParams()
      tokenParams.clientId = clientId
      tokenParams.ttl = 86400000 // 1 day
      tokenParams.capability = s"""{"$clientId:*":["subscribe"]}"""
      val authOptions = AuthOptions()
      val tokenRequest =
        ablyClient.auth.requestToken(tokenParams, null)
      val token = tokenRequest.token

      Right(token)
    catch case ex => Left(ex.getMessage)
