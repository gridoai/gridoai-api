package com.gridoai.adapters.fileStorage

import cats.effect.IO
import com.gridoai.utils.*
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.AccessToken
import com.google.api.services.drive.Drive
import java.util.Date
import java.io.ByteArrayOutputStream
import scala.jdk.CollectionConverters._

object GDriveClient:

  private def buildDriveService(token: String): Drive =
    val expiryTime =
      new Date(System.currentTimeMillis() + 3600 * 1000) // 1 hour later
    val accessToken = new AccessToken(token, expiryTime)
    val credentials = GoogleCredentials.create(accessToken)
    val jsonFactory = GsonFactory.getDefaultInstance()
    val httpTransport = new NetHttpTransport()

    new Drive.Builder(
      httpTransport,
      jsonFactory,
      new HttpCredentialsAdapter(credentials)
    )
      .setApplicationName("GridoAI")
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
