package com.gridoai.services.notifications

import cats.effect.IO
import cats.implicits._
import cats.data.EitherT
import cats.effect.implicits._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

import com.gridoai.auth.AuthData
import com.gridoai.domain._
import com.gridoai.utils._
import com.gridoai.adapters.notifications.generateToken
import com.gridoai.adapters.notifications.NotificationService

def createNotificationServiceToken(authData: AuthData) =
  generateToken[IO](authData.userId)

def notifySearch(
    report: SearchReport,
    user: String
)(implicit
    ns: NotificationService[IO]
): EitherT[IO, String, Unit] =
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
): EitherT[IO, String, Unit] =
  ns.sendNotification(
    topic = s"$user:upload",
    channel = s"$user:upload-status",
    content = status.toString()
  )

def notifySearchProgress[L, R](queries: List[String], userId: String)(
    io: => EitherT[IO, L, R]
)(implicit
    ns: NotificationService[IO]
): EitherT[IO, L, R] =
  (for
    _ <- notifySearch(
      SearchReport(queries = queries, status = SearchStatus.Started),
      userId
    ).start
    res <- io
    - <- notifySearch(
      SearchReport(queries = queries, status = SearchStatus.Success),
      userId
    ).start
  yield res).leftFlatMap: e =>
    for
      _ <- notifySearch(
        SearchReport(queries = queries, status = SearchStatus.Failure),
        userId
      ).start
      err = e
    yield err

def notifyUploadProgress[L, R](id: String)(
    io: => EitherT[IO, L, R]
)(implicit
    ns: NotificationService[IO]
): EitherT[IO, String, Unit] =
  (for
    _ <- notifyUpload(
      UploadStatus.Processing,
      id
    )
    res <- io
    _ <- notifyUpload(
      UploadStatus.Success,
      id
    )
  yield res)
    .leftFlatMap(e =>
      notifyUpload(
        UploadStatus.Failure,
        id
      )
    )
    .start
    >> EitherT.rightT(())
