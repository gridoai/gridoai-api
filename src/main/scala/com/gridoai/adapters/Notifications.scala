package com.gridoai.adapters.notifications

trait NotificationService[F[_]]:
  def sendNotification(
      topic: String,
      channel: String,
      content: String
  ): F[Either[String, Unit]]
