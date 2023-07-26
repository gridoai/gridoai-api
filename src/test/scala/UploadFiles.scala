package com.gridoai.test

import cats.effect.IO

import com.gridoai.models.DocDB
import com.gridoai.models.MockDocDB
import munit.CatsEffectSuite
import sttp.client3.UriContext
import sttp.client3.basicRequest
import sttp.model.Header
import sttp.model.StatusCode
import sttp.model.Part
import sttp.model.MediaType
import cats.implicits.*
import scala.jdk.CollectionConverters.*

import io.circe.parser.*
import io.circe.*
import com.gridoai.models.PostgresClient

object fileMock:
  import java.nio.file.{Files, Paths}
  import sttp.client3.{ByteArrayBody, StringBody}

  def generateFilePartsOfDir(directory: String) =
    val dir = Paths.get(directory)
    val files = Files
      .list(dir)
      .iterator()
      .asScala
      .take(2)
      .filter(_.toFile.isFile())
      .toList

    files.map { path =>
      val name = path.getFileName.toString
      val bytes = Files.readAllBytes(path)
      val contentType = Option(Files.probeContentType(path))
        .flatMap(x => MediaType.parse(x).toOption)

      Part(
        name = "files",
        body = ByteArrayBody(bytes),
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
  given doobie.LogHandler = doobie.LogHandler.jdkLogHandler
  given db: DocDB[IO] = PostgresClient[IO]

  import com.gridoai.adapters.*
  import fileMock._
  test("Uploads a file") {

    val (fileUpload, numFiles) = generateFilePartsOfDir("./src/test/resources/")

    println("sending request")
    for {
      be <- catsBackend
      req = basicRequest
        .post(uri"http://localhost:8080/upload")
        .headers(authHeader)
        .header("Content-Type", "multipart/form-data")
        .multipartBody(fileUpload)
      // Create a request
      response <- req
        .send(be)
      _ <- IO.println(req.toCurl)
      _ <- IO.println(response)

      // Assert that the response status is OK
      _ = assertEquals(response.code, StatusCode.Ok)
      decoded <- IO.fromEither(
        response.body
          .flatMap(decode[List[String]])
          .left
          .map(x => new Exception(x.toString()))
      )

      _ = assertEquals(decoded.length, numFiles)

    } yield ()

  }
}
