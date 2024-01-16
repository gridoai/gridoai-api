package com.gridoai.adapters.fileStorage

import cats.effect.IO
import cats.data.EitherT
import cats.implicits._
import java.io.ByteArrayOutputStream
import scala.jdk.CollectionConverters._
import cats.effect.kernel.Sync
import org.slf4j.LoggerFactory

import com.gridoai.utils._
import com.gridoai.adapters.GoogleClient

object GDriveClient:
  val logger = LoggerFactory.getLogger(getClass.getName)
  def mapMimeTypes: String => Option[String] =
    case "application/vnd.google-apps.presentation" =>
      Some(
        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
      )
    case "application/vnd.google-apps.document" =>
      Some(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      )
    case "application/vnd.google-apps.sheet" |
        "application/vnd.google-apps.spreadsheet" =>
      Some("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    case _ => None

  def apply(token: String): FileStorage[IO] =
    val driveService = GoogleClient.buildDriveService(token)

    new FileStorage[IO]:

      def listFiles(
          folderIds: List[String]
      ): EitherT[IO, String, List[FileMeta]] =
        val query = folderIds
          .map(folderId => s"'$folderId' in parents")
          .mkString(" or ")
        logger.info(s"query: $query")
        Sync[IO]
          .blocking(
            driveService.files
              .list()
              .setQ(query)
              .execute()
              .getFiles
              .asScala
              .toList
              .map(file => FileMeta(file.getId, file.getName, file.getMimeType))
              .asRight
          )
          .asEitherT
          .attempt

      def downloadFiles(
          files: List[FileMeta]
      ): EitherT[IO, String, List[File]] =
        Sync[IO]
          .blocking(
            files
              .map(file =>
                logger.info(
                  s"Downloading file: ${file.name} with mime ${file.mimeType}"
                )
                val outputStream = ByteArrayOutputStream()
                mapMimeTypes(file.mimeType) match
                  case Some(mimeType) =>
                    logger.info("Exporting file from Google Drive...")
                    driveService.files
                      .`export`(file.id, mimeType)
                      .executeMediaAndDownloadTo(outputStream)
                  case None =>
                    logger.info("Downloading file from Google Drive...")
                    driveService.files
                      .get(file.id)
                      .executeMediaAndDownloadTo(outputStream)
                File(meta = file, content = outputStream.toByteArray)
              )
              .asRight
          )
          .asEitherT
          .attempt

      def isFolder(fileId: String): EitherT[IO, String, Boolean] =
        (Sync[IO]
          .blocking:
            val file =
              driveService.files.get(fileId).setFields("mimeType").execute()
            Right(file.getMimeType == "application/vnd.google-apps.folder")
          )
          .asEitherT
          .attempt

      def fileInfo(fileIds: List[String]): EitherT[IO, String, List[FileMeta]] =
        Sync[IO]
          .blocking(
            fileIds
              .map(fileId =>
                val file = driveService.files.get(fileId).execute()
                FileMeta(file.getId, file.getName, file.getMimeType)
              )
              .asRight
          )
          .asEitherT
          .attempt
