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

def importGDriveDocuments(auth: AuthData)(
    fileIds: List[String]
)(using db: DocDB[IO]): IO[Either[String, List[String]]] =
  limitRole(
    auth.role,
    Left(authErrorMsg(Some(auth.role))).pure[IO]
  ):
    println("importing data from gdrive...")
    ClerkClient
      .getUserPublicMetadata(auth.userId)
      .flatMapRight:
        case PublicMetadata(Some(accessToken), Some(refreshToken)) =>
          getGDriveClient(auth.userId, accessToken, refreshToken).flatMapRight:
            gdriveClient =>
              fileIds
                .traverse(fileId =>
                  gdriveClient
                    .isFolder(fileId)
                    .mapRight(isFolder => (fileId, isFolder))
                )
                .map(partitionEithers)
                .mapLeft(_.mkString(","))
                .flatMapRight: elements =>
                  val folders = elements.filter(_._2).map(_._1)
                  val files = elements.filter(!_._2).map(_._1)
                  println(s"Received ${folders.length} folders.")
                  println(s"Received ${files.length} files.")

                  findAllFilesInFolders(gdriveClient, folders)
                    .flatMapRight(newFiles =>
                      if files.length > 0 then
                        gdriveClient
                          .fileInfo(files)
                          .mapRight(filesMeta => filesMeta ++ newFiles)
                      else Right(newFiles).pure[IO]
                    )
                    .flatMapRight(gdriveClient.downloadFiles)
                    .flatMapRight(
                      _.traverse(parseGDriveFileForPersistence)
                        .map(partitionEithers)
                        .mapLeft(_.mkString(","))
                        .flatMapRight(filesToUpload =>
                          db.listDocumentsBySource(
                            filesToUpload.map(_.source),
                            auth.orgId,
                            auth.role
                          ).mapRight(filesToUpdate =>
                            filesToUpload.map(fileToUpload =>
                              filesToUpdate
                                .filter(_.source == fileToUpload.source)
                                .headOption match
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
                        )
                        .flatMapRight(createDocs(auth))
                        .mapRight(_.map(_.name))
                    )

        case _ => Left("Make Google Drive authentication first").pure[IO]

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

def getGDriveClient(
    userId: String,
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
