package com.gridoai.utils

import com.gridoai.adapters.notifications.UploadNotificationService
import cats.effect.IO
import com.gridoai.adapters.notifications.UploadStatus

def notifyIOProgress[L, R](
    id: String,
    notificationService: UploadNotificationService[IO]
)(
    io: => IO[Either[L, R]]
) =
  notificationService
    .notifyUpload(
      UploadStatus.Processing,
      id
    )
    .start >>
    io
      .flatMapRight(_ =>
        notificationService
          .notifyUpload(
            UploadStatus.Success,
            id
          )
      )
      .flatMapLeft(e => {
        notificationService
          .notifyUpload(
            UploadStatus.Failure,
            id
          )

      })
      .start
    >> IO.pure(Right(()))
