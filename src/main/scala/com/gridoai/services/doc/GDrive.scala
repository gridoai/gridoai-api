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
import java.util.UUID

val SCOPES = List("https://www.googleapis.com/auth/drive.readonly")
val HOST = "https://457a-2804-14c-5bc0-95c5-1af2-6a7a-a51f-2aed.ngrok.io"
// val HOST = "https://gridoai-api-5yq2d4shfq-rj.a.run.app"
val WEBHOOK_URI = s"$HOST/gdrive/sync"

def authenticateGDrive(auth: AuthData)(
    code: String,
    redirectUri: String
): IO[Either[String, (String, String)]] =
  limitRole(
    auth.role,
    Left(authErrorMsg(Some(auth.role))).pure[IO]
  ):
    println("Authenticating gdrive...")
    GoogleClient
      .exchangeCodeForTokens(
        code,
        redirectUri,
        SCOPES
      )
      .flatMapRight(ClerkClient.setUserPublicMetadata(auth.userId))

def fetchUserTokens(auth: AuthData): IO[Either[String, (String, String)]] =
  ClerkClient
    .getUserPublicMetadata(auth.userId)
    .flatMapRight:
      case PublicMetadata(Some(x), Some(y)) => Right((x, y)).pure[IO]
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

def findAllFilesAndFolders(
    gdriveClient: FileStorage[IO],
    fileIds: List[String],
    appendFolders: Boolean = false
): IO[Either[String, List[FileMeta]]] =
  partitionFilesFolders(gdriveClient, fileIds).flatMapRight: (folders, files) =>
    val metas = findAllFilesInFolders(gdriveClient, folders)
      !> appendFiles(gdriveClient, files)
    if !appendFolders then metas
    else metas !> appendFiles(gdriveClient, folders)

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
      _.traverse(parseGDriveFileForPersistence)
        .map(partitionEithers)
        .mapLeft(_.mkString(","))
    )
    .flatMapRight(createOrUpdateFiles(auth))
    .mapRight(_.map(_.name))

def createOrUpdateFiles(auth: AuthData)(
    filesToUpload: List[Document]
)(using db: DocDB[IO]): IO[Either[String, List[Document]]] =
  db.listDocumentsBySource(filesToUpload.map(_.source), auth.orgId, auth.role)
    .mapRight(filesToUpdate =>
      filesToUpload.map(fileToUpload =>
        filesToUpdate.filter(_.source == fileToUpload.source).headOption match
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

def getAndAddGDriveDocs(auth: AuthData, fileIds: List[String])(
    gdriveClient: FileStorage[IO]
)(using db: DocDB[IO]) =
  findAllFilesAndFolders(gdriveClient, fileIds)
    !> downloadAndParseFiles(auth, gdriveClient)

def getAndWatchDriveDocs(
    fileIds: List[String]
)(gdriveClient: FileStorage[IO]): IO[Either[String, List[SyncData]]] =
  findAllFilesAndFolders(gdriveClient, fileIds, appendFolders = true)
    .flatMapRight(
      _.map(_.id)
        .traverse(gdriveClient.watchFile(WEBHOOK_URI))
        .map(partitionEithers)
        .mapLeft(_.mkString(","))
    )

def getAndUnwatchDriveDocs(
    fileIds: List[String]
)(gdriveClient: FileStorage[IO]): IO[Either[String, List[String]]] =
  findAllFilesAndFolders(gdriveClient, fileIds, appendFolders = true)
    .flatMapRight(
      _.map(_.id)
        .traverse(gdriveClient.unwatchFile)
        .map(partitionEithers)
        .mapLeft(_.mkString(","))
    )

def importGDriveDocuments(auth: AuthData)(
    fileIds: List[String]
)(using db: DocDB[IO]): IO[Either[String, List[String]]] =
  limitRole(
    auth.role,
    Left(authErrorMsg(Some(auth.role))).pure[IO]
  ):
    println("importing data from gdrive...")

    fetchUserTokens(auth)
      !> getGDriveClient(auth.userId)
      !> getAndAddGDriveDocs(auth, fileIds)

def watchGDriveDocuments(auth: AuthData)(
    fileIds: List[String]
)(using db: DocDB[IO]): IO[Either[String, List[String]]] =
  limitRole(
    auth.role,
    Left(authErrorMsg(Some(auth.role))).pure[IO]
  ):
    println("Watching data from gdrive...")
    fetchUserTokens(auth)
      !> getGDriveClient(auth.userId)
      !> getAndUnwatchDriveDocs(fileIds)

def unwatchGDriveDocuments(auth: AuthData)(
    fileIds: List[String]
)(using db: DocDB[IO]): IO[Either[String, List[String]]] =
  limitRole(
    auth.role,
    Left(authErrorMsg(Some(auth.role))).pure[IO]
  ):
    println("Unwatching data from gdrive...")
    fetchUserTokens(auth)
      !> getGDriveClient(auth.userId)
      !> getAndWatchDriveDocs(fileIds)

// TODO: Add authorization someway in sync requests
// TODO: Store channel expirations and renew them automatically
def syncGDriveDocuments(
    channelId: String,
    state: String,
    resourceId: String
)(implicit db: DocDB[IO]): IO[Either[String, Unit]] =
  println("Syncing a file...")
  println(channelId)
  println(state)
  println(resourceId)
  ().asRight.pure[IO]

def parseGDriveFileForPersistence(
    file: File
): IO[Either[String, Document]] =
  extractAndCleanText(
    file.meta.name,
    file.content,
    parseGoogleMimeTypes(file.meta.mimeType)
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

def parseGoogleMimeTypes(mimeType: String): Option[FileFormat] =
  mimeType match
    case "application/vnd.google-apps.presentation" => Some(FileFormat.PPTX)
    case "application/vnd.google-apps.document"     => Some(FileFormat.DOCX)
    case _                                          => None

def findAllFilesInFolders(
    gdriveClient: FileStorage[IO],
    folders: List[String]
): IO[Either[String, List[FileMeta]]] =
  if folders.length > 0 then
    gdriveClient
      .listFiles(folders)
      .flatMapRight: elements =>
        val (files, newFolders) =
          elements.partition(_.mimeType != "application/vnd.google-apps.folder")
        println(s"find ${files.length} files!")
        println(s"find ${newFolders.length} folders!")
        if newFolders.length > 0 then
          findAllFilesInFolders(gdriveClient, newFolders.map(_.id)).mapRight(
            newFiles => files ++ newFiles
          )
        else Right(files).pure[IO]
  else Right(List()).pure[IO]

def getGDriveClient(userId: String)(
    accessToken: String,
    refreshToken: String
): IO[Either[String, FileStorage[IO]]] =
  val initialGDriveClient = getFileStorage("gdrive")(accessToken)
  initialGDriveClient
    .listFiles(List("root"))
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
