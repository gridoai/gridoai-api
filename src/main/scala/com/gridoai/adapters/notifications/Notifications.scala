package com.gridoai.adapters.notifications
import scala.concurrent.duration._

import cats.effect.kernel.Async
import cats.data.EitherT

import com.gridoai.utils._
import cats.effect.kernel.Deferred
import cats.effect.kernel.Ref
import fs2.concurrent.SignallingRef
import fs2.concurrent.Topic
import cats.effect.kernel.Async
import collection.mutable._

trait NotificationService[F[_]]:
  def sendNotification(
      topic: String,
      channel: String,
      content: String
  ): EitherT[F, String, Unit]

class MockedNotificationService[F[_]: Async]() extends NotificationService[F]:
  val logger = org.slf4j.LoggerFactory.getLogger(getClass)
  val notifications = Map[String, Vector[String]]()
  def sendNotification(
      topic: String,
      channelName: String,
      content: String
  ): EitherT[F, String, Unit] = EitherT.rightT[F, String]:
    logger.info(s"Sending notification to $topic")
    notifications.get(topic) match
      case Some(channels) =>
        notifications.update(topic, channels :+ content)
      case None =>
        notifications.update(topic, Vector(content))

  def waitForNotification(topic: String): String =

    val channel = notifications(topic)
    val last = channel.lastOption
    last match
      case Some(value) =>
        notifications.update(topic, channel.dropRight(1))
        value
      case None =>
        Thread.sleep(1000)
        waitForNotification(topic)

class WhatsAppNotificationService[F[_]: Async]() extends NotificationService[F]:
  val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  def sendNotification(
      topic: String,
      channelName: String,
      content: String
  ): EitherT[F, String, Unit] =
    Async[F]
      .blocking(
        Right(())
      )
      .asEitherT
