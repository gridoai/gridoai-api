package com.gridoai.services.doc

import com.gridoai.domain.*
import com.gridoai.auth.*
import com.gridoai.models.*
import com.gridoai.utils.*
import com.gridoai.adapters.fileStorage.*
import com.gridoai.adapters.GoogleClient
import cats.effect.IO
import cats.implicits.*

def importGDriveDocuments(auth: AuthData)(
    payload: gdriveImportPayload
)(using db: DocDB[IO]): IO[Either[String, List[String]]] =
  // limitRole(
  //   auth.role,
  //   Left(authErrorMsg(Some(auth.role))).pure[IO]
  // ):
  println("importing data from gdrive...")
  GoogleClient
    .exchangeCodeForTokens(
      payload.code,
      "http://localhost:8080/import/gdrive",
      List("https://www.googleapis.com/auth/drive.readonly")
    )
    .pure[IO]
    .flatMapRight: (accessToken, _) =>
      val gdriveClient = getFileStorage("gdrive")(accessToken)
      payload.paths
        .traverse(path => gdriveClient.listContent(path))
        .map(partitionEithers)
        .mapLeft(_.mkString(","))
        .trace
        .mapRight(_ => List("Hi!"))
