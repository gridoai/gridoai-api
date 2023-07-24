package com.gridoai.services.doc

import com.gridoai.domain.*
import com.gridoai.auth.*
import com.gridoai.models.*
import com.gridoai.adapters.fileStorage.*
import cats.effect.IO
import cats.implicits.*

def importGDriveDocuments(auth: AuthData)(
    payload: gdriveImportPayload
)(using db: DocDB[IO]): IO[Either[String, List[String]]] =
  limitRole(
    auth.role,
    Left(authErrorMsg(Some(auth.role))).pure[IO]
  ):
    // TODO: Implement gdrive code exchange and documents download
    Right(List("Hi!")).pure[IO]
