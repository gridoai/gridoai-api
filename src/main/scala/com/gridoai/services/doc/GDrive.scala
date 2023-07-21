package com.gridoai.services.doc

import com.gridoai.domain.*
import com.gridoai.auth.*
import com.gridoai.models.*
import cats.effect.IO

def importGDriveDocuments(auth: AuthData)(
    payload: gdriveImportPayload
)(using db: DocDB[IO]): IO[Either[String, List[String]]] =
  IO.pure(Right(List("hi")))
