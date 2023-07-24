package com.gridoai.adapters.fileStorage

import cats.effect.IO
import com.gridoai.utils.*
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import java.io.{ByteArrayOutputStream, IOException}
import cats.effect.IO
import scala.jdk.CollectionConverters._

object GDriveClient:

  private def buildDriveService(token: String): Drive =
    val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    val jsonFactory = JacksonFactory.getDefaultInstance
    val credential = new GoogleCredential().setAccessToken(accessToken)

    new Drive.Builder(httpTransport, jsonFactory, credential)
      .setApplicationName("Your App Name")
      .build()

  def apply(token: String) = new FileStorage[IO]:
    private val driveService: Drive = buildDriveService(token)

    def listContent(path: String): IO[Either[String, List[String]]] =
      (IO:
        val result = driveService
          .files()
          .list()
          .setQ(s"'$path' in parents")
          .execute()
        val files = result.getFiles.asScala.toList
        Right(files.map(_.getName))
      ) |> attempt

    def downloadFiles(
        files: List[String]
    ): IO[Either[String, List[Array[Byte]]]] =
      (IO:
        val fileContents = files.map: fileId =>
          val outputStream = new ByteArrayOutputStream()
          driveService
            .files()
            .get(fileId)
            .executeMediaAndDownloadTo(outputStream)
          outputStream.toByteArray
        Right(fileContents)
      ) |> attempt
