package com.gridoai.services.notifications

import com.gridoai.auth.AuthData
import com.gridoai.domain.UploadStatus
import com.gridoai.utils._
import com.gridoai.adapters.notifications.generateToken
import cats.effect.IO
import com.gridoai.adapters.notifications.NotificationService

def createNotificationServiceToken(authData: AuthData) =
  generateToken[IO](authData.userId)

def notifySearchQuery(
    query: String,
    user: String
)(implicit
    ns: NotificationService[IO]
): IO[Either[String, Unit]] =
  ns.sendNotification(
    topic = s"$user:chat",
    channel = s"$user:chat-search-query",
    content = query
  )

def notifyUpload(
    status: UploadStatus,
    user: String
)(implicit
    ns: NotificationService[IO]
): IO[Either[String, Unit]] =
  ns.sendNotification(
    topic = s"$user:upload",
    channel = s"$user:upload-status",
    content = status.toString()
  )

def notifyUploadProgress[L, R](id: String)(
    io: => IO[Either[L, R]]
)(implicit
    ns: NotificationService[IO]
) =
  notifyUpload(
    UploadStatus.Processing,
    id
  ) >>
    io
      .flatMapRight(_ =>
        notifyUpload(
          UploadStatus.Success,
          id
        )
      )
      .flatMapLeft(e =>
        notifyUpload(
          UploadStatus.Failure,
          id
        )
      )
      .start
    >> IO.pure(Right(()))
