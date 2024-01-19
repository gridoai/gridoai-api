package com.gridoai.services.doc

import cats.effect.IO
import cats.implicits._
import cats.data.EitherT
import java.util.UUID
import org.slf4j.LoggerFactory

import com.gridoai.domain._
import com.gridoai.auth._
import com.gridoai.models._
import com.gridoai.utils._
import com.gridoai.adapters.fileStorage._
import com.gridoai.adapters.GoogleClient
import com.gridoai.adapters.clerk.ClerkClient
import com.gridoai.adapters.clerk.PublicMetadata
import com.gridoai.parsers.FileFormat
import com.gridoai.endpoints.RefreshTokenPayload
import com.gridoai.adapters.notifications.NotificationService
import com.gridoai.services.notifications.notifyUploadProgress

val SCOPES = List("https://www.googleapis.com/auth/drive.readonly")
object GDrive:
  def auth(auth: AuthData)(
      code: String,
      redirectUri: String
  ): EitherT[IO, String, (String, String)] =
    limitRole(
      auth.role,
      Left(authErrorMsg(Some(auth.role))).pure[IO].asEitherT
    ):
      logger.info("Authenticating gdrive...")
      GoogleClient
        .exchangeCodeForTokens(
          code,
          redirectUri,
          SCOPES
        )
        .flatMap(ClerkClient.setGDriveMetadata(auth.userId))

  def fetchUserTokens(auth: AuthData): EitherT[IO, String, (String, String)] =
    ClerkClient.user
      .getPublicMetadata(auth.userId)
      .flatMapEither:
        case PublicMetadata(Some(x), Some(y), _, _) => Right((x, y))
        case _ => Left("Make Google Drive authentication first")

  def partitionFilesFolders(
      gdriveClient: FileStorage[IO],
      fileIds: List[String]
  ): EitherT[IO, String, (List[String], List[String])] =
    fileIds
      .traverse(fileId =>
        gdriveClient.isFolder(fileId).map(isFolder => (fileId, isFolder))
      )
      .map(elements =>
        (elements.filter(_._2).map(_._1), elements.filter(!_._2).map(_._1))
      )

  def appendFiles(
      gdriveClient: FileStorage[IO],
      newFiles: List[String]
  )(
      files: List[FileMeta]
  ): EitherT[IO, String, List[FileMeta]] =
    if newFiles.nonEmpty then
      gdriveClient
        .fileInfo(newFiles)
        .map(filesMeta => filesMeta ++ files)
    else EitherT.rightT(files)

  def downloadAndParseFiles(auth: AuthData, gdriveClient: FileStorage[IO])(
      files: List[FileMeta]
  )(using db: DocDB[IO]): EitherT[IO, String, List[String]] =
    gdriveClient
      .downloadFiles(files)
      .flatMap(_.traverse(parseFileForPersistence))
      .flatMap(createOrUpdateFiles(auth))
      .map(_.map(_.name))

  def getAndAddDocs(auth: AuthData, fileIds: List[String])(
      gdriveClient: FileStorage[IO]
  )(using db: DocDB[IO]) =
    partitionFilesFolders(gdriveClient, fileIds).flatMap: (folders, files) =>
      findAllFilesInFolders(gdriveClient, folders)
        !> appendFiles(gdriveClient, files)
        !> downloadAndParseFiles(auth, gdriveClient)

  def importDocs(auth: AuthData)(
      fileIds: List[String]
  )(implicit
      db: DocDB[IO],
      ns: NotificationService[IO]
  ): EitherT[IO, String, Unit] =
    limitRole(
      auth.role,
      Left(authErrorMsg(Some(auth.role))).pure[IO].asEitherT
    ):
      logger.info("importing data from gdrive...")
      notifyUploadProgress(auth.userId):
        fetchUserTokens(auth)
          !> getClient(auth.userId)
          !> getAndAddDocs(auth, fileIds)

  def refreshAndSetToken(refreshToken: String, userId: String) = GoogleClient
    .refreshToken(refreshToken)
    .flatMap(ClerkClient.setGDriveMetadata(userId))

  def refreshToken(auth: AuthData)(
      refreshTokenPayload: RefreshTokenPayload
  ): EitherT[IO, String, (String, String)] =
    limitRole(
      auth.role,
      Left(authErrorMsg(Some(auth.role))).pure[IO].asEitherT
    ):
      logger.info("refreshing gdrive token...")
      refreshAndSetToken(refreshTokenPayload.refreshToken, auth.userId)

  def parseFileForPersistence(
      file: File
  ): EitherT[IO, String, Document] =
    extractAndCleanText(
      file.meta.name,
      file.content,
      parseGoogleMimeTypes(file.meta.mimeType, file.meta.name)
    )
      .leftMap(_.toString)
      .map(content =>
        Document(
          uid = UUID.randomUUID(),
          name = file.meta.name,
          source = Source.GDrive(file.meta.id),
          content = content
        )
      )

  def parseGoogleMimeTypes(
      mimeType: String,
      name: String = ""
  ): Option[FileFormat] =
    mimeType match
      case "application/vnd.google-apps.presentation" => Some(FileFormat.PPTX)
      case "application/vnd.google-apps.document"     => Some(FileFormat.DOCX)
      case "application/octet-stream" => FileFormat.ofFilename(name)
      case unknown                    => Some(FileFormat.fromString(unknown))

  val supportedMimes = List(
    "application/vnd.google-apps.presentation",
    "application/vnd.google-apps.document",
    "text/plain",
    "application/pdf",
    "text/plain",
    "text/markdown",
    "text/x-markdown",
    "application/vnd.google-apps.folder",
    "application/octet-stream"
  )

  def findAllFilesInFolders(
      gdriveClient: FileStorage[IO],
      folders: List[String]
  ): EitherT[IO, String, List[FileMeta]] =
    if folders.nonEmpty then
      gdriveClient
        .listFiles(folders)
        .flatMap: elements =>
          val validFiles = elements.filter(supportedMimes contains _.mimeType)
          logger.info(s"find ${validFiles.length} valid files!")
          validFiles.foreach(f => logger.info(s"file: ${f.name}"))
          val (files, newFolders) =
            validFiles.partition(
              _.mimeType != "application/vnd.google-apps.folder"
            )
          logger.info(s"find ${files.length} files!")
          logger.info(s"find ${newFolders.length} folders!")
          if newFolders.nonEmpty then
            findAllFilesInFolders(gdriveClient, newFolders.map(_.id)).map(
              newFiles => files ++ newFiles
            )
          else EitherT.rightT(files)
    else EitherT.rightT(List.empty)

  def getClient(userId: String)(
      accessToken: String,
      refreshToken: String
  ): EitherT[IO, String, FileStorage[IO]] =
    val initialGDriveClient = getFileStorage("gdrive")(accessToken)
    initialGDriveClient
      .listFiles(List("root"))
      .map(_ => initialGDriveClient)
      .leftFlatMap(_ =>
        logger.info("Trying to refresh token...")
        refreshAndSetToken(refreshToken, userId)
          .map((newAccessToken, _) => getFileStorage("gdrive")(newAccessToken))
      )
