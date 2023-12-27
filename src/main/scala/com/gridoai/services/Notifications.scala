package com.gridoai.services.notifications

import com.gridoai.auth.AuthData
import com.gridoai.domain._
import com.gridoai.utils._
import com.gridoai.adapters.notifications.generateToken
import cats.effect.IO
import cats.implicits._
import cats.effect.implicits._
import com.gridoai.adapters.notifications.NotificationService
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

def createNotificationServiceToken(authData: AuthData) =
  generateToken[IO](authData.userId)

def notifySearch(
    report: SearchReport,
    user: String
)(implicit
    ns: NotificationService[IO]
): IO[Either[String, Unit]] =
  ns.sendNotification(
    topic = s"$user:chat",
    channel = s"$user:chat-search",
    content = report.asJson.noSpaces
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

def notifySearchProgress[L, R](queries: List[String], userId: String)(
    io: => IO[Either[L, R]]
)(implicit
    ns: NotificationService[IO]
) =
  notifySearch(
    SearchReport(queries = queries, status = SearchStatus.Started),
    userId
  ).start >>
    io
      .flatMapRight: res =>
        notifySearch(
          SearchReport(queries = queries, status = SearchStatus.Success),
          userId
        ).start >> IO.pure(res.asRight)
      .flatMapLeft: e =>
        notifySearch(
          SearchReport(queries = queries, status = SearchStatus.Failure),
          userId
        ).start >> IO.pure(e.asLeft)

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
