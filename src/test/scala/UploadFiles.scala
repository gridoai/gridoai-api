package com.gridoai.test

import munit.CatsEffectSuite
import cats.effect.IO

import com.gridoai.models.DocDB
import com.gridoai.utils.*
import sttp.model.Part
import sttp.model.MediaType
import scala.jdk.CollectionConverters.*

import com.gridoai.models.PostgresClient
import com.gridoai.auth.AuthData
import com.gridoai.endpoints.FileUpload
import com.gridoai.services.doc.uploadDocuments
import com.gridoai.domain.Plan
import com.gridoai.adapters.notifications.MockedNotificationService
import com.gridoai.adapters.notifications.NotificationService
import cats.data.EitherT
object fileMock:
  import java.nio.file.{Files, Paths}
  import sttp.client3.{StringBody}

  def generateFilePartsOfDir(directory: String) =
    val dir = Paths.get(directory)
    val files = Files
      .list(dir)
      .iterator()
      .asScala
      .take(2)
      .filter(_.toFile.isFile())
      .toList
      .trace

    files.map { path =>
      val name = path.getFileName.toString
      val contentType = Option(Files.probeContentType(path))
        .flatMap(x => MediaType.parse(x).toOption)

      Part(
        name = "files",
        body = path.toFile(),
        contentType = contentType,
        fileName = Some(name)
      )

    } -> files.length

  def generateRandomFiles(n: Int) =
    (1 to n).toList.map(i =>
      Part(
        name = s"files",
        body = StringBody(s"file content ${i}", "UTF-8"),
        contentType = Some(MediaType.TextPlain),
        fileName = Some(s"file-$i.txt")
      )
    )
class UploadApi extends CatsEffectSuite {
  given doobie.LogHandler[IO] = doobie.LogHandler.jdkLogHandler
  given db: DocDB[IO] = PostgresClient[IO](PostgresClient.getSyncTransactor)
  given ns: MockedNotificationService[IO] = MockedNotificationService[IO]

  import fileMock._
  test("Uploads a file") {

    val (fileUpload, numFiles) =
      generateFilePartsOfDir("./src/test/resources/")

    println("sending request")
    (for {
      uploadResult <- uploadDocuments(
        AuthData("org1", "admin", "admin_1", Plan.Enterprise, None)
      )(FileUpload(fileUpload))

      msg = ns.waitForNotification("admin_1:upload")
      _ = println(msg)
      _ = assert(!msg.isEmpty)

    } yield ()).value
  }
}
