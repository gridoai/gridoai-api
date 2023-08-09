package com.gridoai.adapters.fileStorage

import cats.effect.IO
import com.gridoai.utils.*
import com.gridoai.adapters.GoogleClient

import java.io.ByteArrayOutputStream
import scala.jdk.CollectionConverters._

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
      def listFolders(folderId: String): IO[Either[String, List[FileMeta]]] =
        (IO:
          val result = driveService
            .files()
            .list()
            .setQ(
              s"'$folderId' in parents and mimeType='application/vnd.google-apps.folder'"
            )
            .execute()
          val files = result.getFiles.asScala.toList
          Right(
            files.map(file =>
              FileMeta(file.getId, file.getName, file.getMimeType)
            )
          )
        ) |> attempt

      def listFiles(folderId: String): IO[Either[String, List[FileMeta]]] =
        (IO:
          val result = driveService
            .files()
            .list()
            .setQ(
              s"'$folderId' in parents and mimeType!='application/vnd.google-apps.folder'"
            )
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
        (IO:
          val fileContents = files.map: file =>
            val outputStream = new ByteArrayOutputStream()
            if List(
                "application/vnd.google-apps.presentation",
                "application/vnd.google-apps.document",
                "application/vnd.google-apps.sheet"
              ).contains(file.mimeType)
            then
              driveService
                .files()
                .`export`(file.id, file.mimeType)
                .executeMediaAndDownloadTo(outputStream)
            else
              driveService
                .files()
                .get(file.id)
                .executeMediaAndDownloadTo(outputStream)
            File(meta = file, content = outputStream.toByteArray)
          Right(fileContents)
        ) |> attempt
