package com.gridoai.adapters.fileStorage

import cats.effect.IO
import com.gridoai.utils.*
import com.gridoai.adapters.GoogleClient

import java.io.ByteArrayOutputStream
import scala.jdk.CollectionConverters._
import cats.effect.kernel.Sync

object GDriveClient:

  def mapMimeTypes: String => Option[String] =
    case "application/vnd.google-apps.presentation" =>
      Some(
        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
      )
    case "application/vnd.google-apps.document" =>
      Some(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      )
    case "application/vnd.google-apps.sheet" =>
      Some("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    case _ => None

  def apply(token: String): FileStorage[IO] =
    val driveService = GoogleClient.buildDriveService(token)

    new FileStorage[IO]:

      def listFiles(
          folderIds: List[String]
      ): IO[Either[String, List[FileMeta]]] =
        (Sync[IO].blocking:
          val query = folderIds
            .map(folderId => s"'$folderId' in parents")
            .mkString(" or ")
          println(s"query: $query")
          val result = driveService
            .files()
            .list()
            .setQ(query)
            .execute()
          val files = result.getFiles.asScala.toList
          Right(
            files.map(file =>
              FileMeta(file.getId, file.getName, file.getMimeType)
            )
          )
        ) |> attempt

      def downloadFiles(
          files: List[FileMeta]
      ): IO[Either[String, List[File]]] =
        (Sync[IO].blocking:
          val fileContents = files.map: file =>
            val outputStream = new ByteArrayOutputStream()
            mapMimeTypes(file.mimeType) match
              case Some(mimeType) =>
                println("Exporting file from Google Drive...")
                driveService
                  .files()
                  .`export`(file.id, mimeType)
                  .executeMediaAndDownloadTo(outputStream)
              case None =>
                println("Downloading file from Google Drive...")
                driveService
                  .files()
                  .get(file.id)
                  .executeMediaAndDownloadTo(outputStream)
            File(meta = file, content = outputStream.toByteArray)
          Right(fileContents)
        ) |> attempt

      def isFolder(fileId: String): IO[Either[String, Boolean]] =
        (Sync[IO].blocking:
          val file =
            driveService.files().get(fileId).setFields("mimeType").execute()
          Right(file.getMimeType == "application/vnd.google-apps.folder")
        ) |> attempt

      def fileInfo(fileIds: List[String]): IO[Either[String, List[FileMeta]]] =
        (Sync[IO].blocking:
          val fileMetas = fileIds.map: fileId =>
            val file = driveService.files().get(fileId).execute()
            FileMeta(file.getId, file.getName, file.getMimeType)
          Right(fileMetas)
        ) |> attempt
