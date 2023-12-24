package com.gridoai.utils

import com.gridoai.adapters.notifications.UploadNotificationService
import cats.effect.IO
import com.gridoai.adapters.notifications.UploadStatus

def notifyUploadProgress[L, R](id: String)(
    io: => IO[Either[L, R]]
)(implicit
    notificationService: UploadNotificationService[IO]
) =
  notificationService
    .notifyUpload(
      UploadStatus.Processing,
      id
    ) >>
    io
      .flatMapRight(_ =>
        notificationService
          .notifyUpload(
            UploadStatus.Success,
            id
          )
      )
      .flatMapLeft(e =>
        notificationService
          .notifyUpload(
            UploadStatus.Failure,
            id
          )
      )
      .start
    >> IO.pure(Right(()))
