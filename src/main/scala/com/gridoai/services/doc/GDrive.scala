package com.gridoai.services.doc

import com.gridoai.domain.*
import com.gridoai.auth.*
import com.gridoai.models.*
import com.gridoai.utils.*
import com.gridoai.adapters.fileStorage.*
import com.gridoai.adapters.GoogleClient
import com.gridoai.adapters.ClerkClient
import com.gridoai.adapters.PublicMetadata
import cats.effect.IO
import cats.implicits.*
import com.gridoai.parsers.FileFormat

val SCOPES = List("https://www.googleapis.com/auth/drive.readonly")

def authenticateGDrive(auth: AuthData)(
    code: String,
    redirectUri: String
): IO[Either[String, (String, String)]] =
  // limitRole(
  //   auth.role,
  //   Left(authErrorMsg(Some(auth.role))).pure[IO]
  // ):
  println("Authenticating gdrive...")
  GoogleClient
    .exchangeCodeForTokens(
      code,
      redirectUri,
      SCOPES
    )
    .flatMapRight(ClerkClient.setUserPublicMetadata(auth.userId))

def importGDriveDocuments(auth: AuthData)(
    paths: List[String]
)(using db: DocDB[IO]): IO[Either[String, List[String]]] =
  // limitRole(
  //   auth.role,
  //   Left(authErrorMsg(Some(auth.role))).pure[IO]
  // ):
  println("importing data from gdrive...")
  ClerkClient
    .getUserPublicMetadata(auth.userId)
    .flatMapRight:
      case PublicMetadata(Some(accessToken), Some(refreshToken)) =>
        getGDriveClient(auth.userId, accessToken, refreshToken).flatMapRight:
          gdriveClient =>
            findAllFilesInPaths(gdriveClient, paths)
              .flatMapRight(gdriveClient.downloadFiles)
              .flatMapRight(
                _.traverse(parseGDriveFileForPersistence)
                  .map(partitionEithers)
                  .mapLeft(_.mkString(","))
                  .flatMapRight(createDocs(auth))
                  .mapRight(_.map(_.name))
              )

      case _ => Left("Make Google Drive authentication first").pure[IO]

def parseGDriveFileForPersistence(
    file: File
): IO[Either[String, DocumentCreationPayload]] =
  extractAndCleanText(
    file.meta.name,
    file.content,
    parseGoogleMimeTypes(file.meta.mimeType)
  )
    .mapLeft(_.toString)
    .mapRight(content =>
      DocumentCreationPayload(
        name = file.meta.name,
        source = "gdrive",
        content = content
      )
    )

def parseGoogleMimeTypes(mimeType: String): Option[FileFormat] =
  mimeType match
    case "application/vnd.google-apps.presentation" => Some(FileFormat.PPTX)
    case "application/vnd.google-apps.document"     => Some(FileFormat.DOCX)
    case _                                          => None

def findAllFilesInPaths(
    gdriveClient: FileStorage[IO],
    paths: List[String]
): IO[Either[String, List[FileMeta]]] =
  paths
    .traverse(findAllFilesInPath(gdriveClient))
    .map(partitionEithers)
    .mapLeft(_.mkString(","))
    .mapRight(_.flatten.distinct)

def findAllFilesInPath(gdriveClient: FileStorage[IO])(
    path: String
): IO[Either[String, List[FileMeta]]] =
  gdriveClient
    .listFiles(path)
    .flatMapRight: currentFiles =>
      println(s"files in $path: ${currentFiles.map(_.id)}")
      gdriveClient
        .listFolders(path)
        .flatMapRight: folders =>
          println(s"folders in $path: ${folders.map(_.id)}")
          folders
            .map(_.id)
            .traverse(findAllFilesInPath(gdriveClient))
            .map(partitionEithers)
            .mapLeft(_.mkString(","))
            .mapRight(newFiles => currentFiles ++ newFiles.flatten)

def getGDriveClient(
    userId: String,
    accessToken: String,
    refreshToken: String
): IO[Either[String, FileStorage[IO]]] =
  val initialGDriveClient = getFileStorage("gdrive")(accessToken)
  initialGDriveClient
    .listFiles("root")
    .flatMap:
      case Right(_) => Right(initialGDriveClient).pure[IO]
      case Left(_) =>
        println("Trying to refresh token...")
        GoogleClient
          .refreshToken(refreshToken)
          .flatMapRight(ClerkClient.setUserPublicMetadata(userId))
          .mapRight((newAccessToken, _) =>
            getFileStorage("gdrive")(newAccessToken)
          )
