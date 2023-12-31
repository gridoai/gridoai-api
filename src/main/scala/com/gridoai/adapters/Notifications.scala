package com.gridoai.adapters.notifications

import cats.effect.kernel.Async

trait NotificationService[F[_]]:
  def sendNotification(
      topic: String,
      channel: String,
      content: String
  ): F[Either[String, Unit]]

class MockedNotificationService[F[_]: Async]() extends NotificationService[F]:
  val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  def sendNotification(
      topic: String,
      channelName: String,
      content: String
  ): F[Either[String, Unit]] =
    Async[F].blocking:
      Thread.sleep(200)
      Right(())

class WhatsAppNotificationService[F[_]: Async]() extends NotificationService[F]:
  val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  def sendNotification(
      topic: String,
      channelName: String,
      content: String
  ): F[Either[String, Unit]] =
    Async[F].blocking:
      Right(())
