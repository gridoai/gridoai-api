package com.gridoai.adapters.notifications

trait NotificationService[F[_]] {
  def sendNotification(
      topic: String,
      channel: String,
      content: String
  ): F[Either[String, Unit]]
}

enum UploadStatus:
  case Failure, Success, Scheduled, Processing

trait UploadNotificationService[F[_]] {
  def notifyUpload(
      status: UploadStatus,
      userId: String
  ): F[Either[String, Unit]]
}

trait UploadNotificationPersister[F[_]]:
  def persistUploadNotification(
      status: UploadStatus,
      fileId: String
  ): F[Either[String, Unit]]
