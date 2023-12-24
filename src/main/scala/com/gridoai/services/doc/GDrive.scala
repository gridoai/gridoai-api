package com.gridoai.services.doc

import com.gridoai.domain.*
import com.gridoai.auth.*
import com.gridoai.models.*
import com.gridoai.utils.*
import com.gridoai.adapters.fileStorage.*
import com.gridoai.adapters.GoogleClient
import com.gridoai.adapters.clerk.ClerkClient
import com.gridoai.adapters.clerk.PublicMetadata
import cats.effect.IO
import cats.implicits.*
import com.gridoai.parsers.FileFormat
import java.util.UUID
import org.slf4j.LoggerFactory
import com.gridoai.endpoints.RefreshTokenPayload
import com.gridoai.adapters.notifications.UploadNotificationService
import com.gridoai.adapters.notifications.UploadStatus

val SCOPES = List("https://www.googleapis.com/auth/drive.readonly")
object GDrive:
  def auth(auth: AuthData)(
      code: String,
      redirectUri: String
  ): IO[Either[String, (String, String)]] =
    limitRole(
      auth.role,
      Left(authErrorMsg(Some(auth.role))).pure[IO]
    ):
      logger.info("Authenticating gdrive...")
      GoogleClient
        .exchangeCodeForTokens(
          code,
          redirectUri,
          SCOPES
        )
        .flatMapRight(ClerkClient.setGDriveMetadata(auth.userId))

  def fetchUserTokens(auth: AuthData): IO[Either[String, (String, String)]] =
    ClerkClient.user
      .getPublicMetadata(auth.userId)
      .flatMapRight:
        case PublicMetadata(Some(x), Some(y), _, _) => Right((x, y)).pure[IO]
        case _ => Left("Make Google Drive authentication first").pure[IO]

  def partitionFilesFolders(
      gdriveClient: FileStorage[IO],
      fileIds: List[String]
  ): IO[Either[String, (List[String], List[String])]] =
    fileIds
      .traverse(fileId =>
        gdriveClient.isFolder(fileId).mapRight(isFolder => (fileId, isFolder))
      )
      .map(partitionEithers)
      .mapLeft(_.mkString(","))
      .mapRight(elements =>
        (elements.filter(_._2).map(_._1), elements.filter(!_._2).map(_._1))
      )

  def appendFiles(
      gdriveClient: FileStorage[IO],
      newFiles: List[String]
  )(
      files: List[FileMeta]
  ): IO[Either[String, List[FileMeta]]] =
    if newFiles.nonEmpty then
      gdriveClient
        .fileInfo(newFiles)
        .mapRight(filesMeta => filesMeta ++ files)
    else Right(files).pure[IO]

  def downloadAndParseFiles(auth: AuthData, gdriveClient: FileStorage[IO])(
      files: List[FileMeta]
  )(using db: DocDB[IO]): IO[Either[String, List[String]]] =
    gdriveClient
      .downloadFiles(files)
      .flatMapRight(
        _.traverse(parseFileForPersistence)
          .map(partitionEithers)
          .mapLeft(_.mkString(","))
      )
      .flatMapRight(createOrUpdateFiles(auth))
      .mapRight(_.map(_.name))

  def createOrUpdateFiles(auth: AuthData)(
      filesToUpload: List[Document]
  )(using db: DocDB[IO]): IO[Either[String, List[Document]]] =
    logger.info(s"Creating or updating ${filesToUpload.length} files...")
    db.listDocumentsBySource(filesToUpload.map(_.source), auth.orgId, auth.role)
      .mapRight(filesToUpdate =>
        filesToUpload.map(fileToUpload =>
          filesToUpdate.find(_.source == fileToUpload.source) match
            case Some(fileToUpdate) =>
              Document(
                uid = fileToUpdate.uid,
                name = fileToUpload.name,
                source = fileToUpload.source,
                content = fileToUpload.content
              )
            case None => fileToUpload
        )
      )
      .flatMapRight(upsertDocs(auth))

  def getAndAddDocs(auth: AuthData, fileIds: List[String])(
      gdriveClient: FileStorage[IO]
  )(using db: DocDB[IO]) =
    partitionFilesFolders(gdriveClient, fileIds).flatMapRight:
      (folders, files) =>
        findAllFilesInFolders(gdriveClient, folders)
          !> appendFiles(gdriveClient, files)
          !> downloadAndParseFiles(auth, gdriveClient)

  def importDocs(auth: AuthData)(
      fileIds: List[String]
  )(implicit
      db: DocDB[IO],
      ns: UploadNotificationService[IO]
  ): IO[Either[String, Unit]] =
    limitRole(
      auth.role,
      Left(authErrorMsg(Some(auth.role))).pure[IO]
    ):
      logger.info("importing data from gdrive...")
      notifyIOProgress(auth.userId, ns):
        fetchUserTokens(auth)
          !> getClient(auth.userId)
          !> getAndAddDocs(auth, fileIds)

  def refreshAndSetToken(refreshToken: String, userId: String) = GoogleClient
    .refreshToken(refreshToken)
    .flatMapRight(ClerkClient.setGDriveMetadata(userId))

  def refreshToken(auth: AuthData)(
      refreshTokenPayload: RefreshTokenPayload
  ): IO[Either[String, (String, String)]] =
    limitRole(
      auth.role,
      Left(authErrorMsg(Some(auth.role))).pure[IO]
    ):
      logger.info("refreshing gdrive token...")
      refreshAndSetToken(refreshTokenPayload.refreshToken, auth.userId)

  def parseFileForPersistence(
      file: File
  ): IO[Either[String, Document]] =
    extractAndCleanText(
      file.meta.name,
      file.content,
      parseGoogleMimeTypes(file.meta.mimeType, file.meta.name)
    )
      .mapLeft(_.toString)
      .mapRight(content =>
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
  ): IO[Either[String, List[FileMeta]]] =
    if folders.nonEmpty then
      gdriveClient
        .listFiles(folders)
        .flatMapRight: elements =>
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
            findAllFilesInFolders(gdriveClient, newFolders.map(_.id)).mapRight(
              newFiles => files ++ newFiles
            )
          else Right(files).pure[IO]
    else Right(List()).pure[IO]

  def getClient(userId: String)(
      accessToken: String,
      refreshToken: String
  ): IO[Either[String, FileStorage[IO]]] =
    val initialGDriveClient = getFileStorage("gdrive")(accessToken)
    initialGDriveClient
      .listFiles(List("root"))
      .flatMap:
        case Right(_) => Right(initialGDriveClient).pure[IO]
        case Left(_) =>
          logger.info("Trying to refresh token...")
          refreshAndSetToken(refreshToken, userId)
            .mapRight((newAccessToken, _) =>
              getFileStorage("gdrive")(newAccessToken)
            )
