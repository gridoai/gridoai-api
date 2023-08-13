package com.gridoai.adapters.fileStorage

import cats.effect.IO
import cats.implicits.*
import com.gridoai.utils.*
import com.gridoai.adapters.GoogleClient

import java.io.ByteArrayOutputStream
import scala.jdk.CollectionConverters._
import cats.effect.kernel.Sync
import com.google.api.services.drive.model.Channel
import java.util.UUID

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
        val query = folderIds
          .map(folderId => s"'$folderId' in parents")
          .mkString(" or ")
        println(s"query: $query")
        Sync[IO].blocking(
          driveService
            .files()
            .list()
            .setQ(query)
            .execute()
            .getFiles
            .asScala
            .toList
            .map(file => FileMeta(file.getId, file.getName, file.getMimeType))
            .asRight
        ) |> attempt

      def downloadFiles(
          files: List[FileMeta]
      ): IO[Either[String, List[File]]] =
        Sync[IO].blocking(
          files
            .map(file =>
              val outputStream = ByteArrayOutputStream()
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
            )
            .asRight
        ) |> attempt

      def isFolder(fileId: String): IO[Either[String, Boolean]] =
        (Sync[IO].blocking:
          val file =
            driveService.files().get(fileId).setFields("mimeType").execute()
          Right(file.getMimeType == "application/vnd.google-apps.folder")
        ) |> attempt

      def fileInfo(fileIds: List[String]): IO[Either[String, List[FileMeta]]] =
        Sync[IO].blocking(
          fileIds
            .map(fileId =>
              val file = driveService.files().get(fileId).execute()
              FileMeta(file.getId, file.getName, file.getMimeType)
            )
            .asRight
        ) |> attempt

      def watchFile(webhookUrl: String)(
          fileId: String
      ): IO[Either[String, SyncData]] =
        (Sync[IO].blocking:
          println(s"Making channel to watch $fileId...")
          val sevenDaysInMillis: Long = 7L * 24 * 60 * 60 * 1000
          val expirationTime = System.currentTimeMillis() + sevenDaysInMillis
          val channel = new Channel()
            .setId(
              UUID.randomUUID.toString
            ) // A unique ID for this channel
            .setType("web_hook")
            .setAddress(webhookUrl)
            .setExpiration(expirationTime)

          val builtChannel =
            driveService.files().watch(fileId, channel).execute()
          SyncData(builtChannel.getId, builtChannel.getResourceId).asRight
        ) |> attempt

      def unwatchFile(
          sync: SyncData
      ): IO[Either[String, Unit]] =
        (Sync[IO].blocking:
          println(
            s"Stopping channel $sync..."
          )
          val channel = new Channel()
            .setId(sync.id) // The unique ID of the channel you want to stop
            .setResourceId(sync.resourceId) // The ID of the watched resource

          driveService.channels().stop(channel).execute()
          ().asRight
        ) |> attempt
