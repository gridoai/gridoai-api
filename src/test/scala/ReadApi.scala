package com.gridoai.test

import cats.effect.IO
import com.gridoai.auth.makeMockedToken
import com.gridoai.domain.*
import com.gridoai.endpoints.*
import com.gridoai.models.DocDB
import com.gridoai.models.MockDocDB
import com.gridoai.utils.trace
import fs2.Stream
import fs2.text.utf8
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import cats.implicits._
import munit.CatsEffectSuite
import sttp.client3.UriContext
import sttp.client3.basicRequest
import sttp.client3.testing.SttpBackendStub
import sttp.model.Header
import sttp.model.StatusCode
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.server.ServerEndpoint
import com.gridoai.models.PostgresClient
import com.gridoai.utils._
import com.gridoai.adapters.notifications.NotificationService
import com.gridoai.adapters.notifications.MockedNotificationService
import com.gridoai.utils.LRUCache
import com.gridoai.models.MessageDB
import com.gridoai.models.RedisClient
import com.gridoai.adapters.emailApi.{EmailAPI, InMemoryEmailer}

val authHeader = Header("Authorization", s"Bearer ${makeMockedToken}")
class API extends CatsEffectSuite {
  given doobie.LogHandler[IO] = doobie.LogHandler.jdkLogHandler
  given db: DocDB[IO] = PostgresClient[IO](PostgresClient.getSyncTransactor)
  given ns: NotificationService[IO] = MockedNotificationService[IO]
  val redis = RedisClient.getRedis[IO].use(IO(_)).unsafeRunSync()
  given messageDb: MessageDB[IO] = RedisClient[IO](redis)
  given emailApi: EmailAPI[IO] = InMemoryEmailer[IO]()
  test("health check should return OK") {

    val response = basicRequest
      .get(uri"http://test.com/health")
      .send(serverStubOf(withService().healthCheck))

    assertIO(response.map(_.body), Right("OK"))
  }
  test("Can't get documents without auth") {
    val responseWithoutAuth = basicRequest
      .get(uri"http://test.com/documents")
      .send(serverStubOf(withService().listDocs))

    assertIO(responseWithoutAuth.map(_.code), StatusCode.Unauthorized)
  }
  test("Can't create documents without auth") {
    val responseWithoutAuth = basicRequest
      .post(uri"http://test.com/documents")
      .send(serverStubOf(withService().createDocument))

    assertIO(responseWithoutAuth.map(_.code), StatusCode.Unauthorized)
  }
  test("Can't delete documents without auth") {
    val responseWithoutAuth = basicRequest
      .delete(uri"http://test.com/documents/123")
      .send(serverStubOf(withService().deleteDoc))

    assertIO(responseWithoutAuth.map(_.code), StatusCode.Unauthorized)
  }
  test("Can't search documents without auth") {
    val responseWithoutAuth = basicRequest
      .post(uri"http://test.com/search")
      .body(
        SearchPayload(
          queries = List("foo"),
          tokenLimit = 1000,
          llmName = "Gpt35Turbo",
          scope = None
        ).asJson.toString
      )
      .send(serverStubOf(withService().searchDocs))

    assertIO(responseWithoutAuth.map(_.code), StatusCode.Unauthorized)
  }

  test("Creates a document") {
    val authenticatedRequest = basicRequest
      .post(uri"http://test.com/documents")
      .headers(authHeader)
      .body(
        DocumentCreationPayload(
          name = "Sky observations",
          content = "The sky is blue"
        ).asJson.toString
      )
      .send(serverStubOf(withService().createDocument))
      .trace("create doc")

    assertIO(authenticatedRequest.map(_.code), StatusCode.Ok)
  }

  test("Searches for a chunk") {

    for
      authenticatedRequest <- basicRequest
        .post(uri"http://test.com/search")
        .headers(authHeader)
        .body(
          SearchPayload(
            queries = List("foo"),
            tokenLimit = 1000,
            llmName = "Gpt35Turbo",
            scope = None
          ).asJson.toString
        )
        .send(serverStubOf(withService().searchDocs))
        .trace("Searches chunks")
        .map(_.body flatMap decode[List[Chunk]])
      _ = assert(authenticatedRequest.isRight)
    yield ()

  }

  test("Ask LLM") {
    val backendStub =
      serverStubOf(
        withService().askLLM
      )

    basicRequest
      .post(uri"http://test.com/ask")
      .headers(authHeader)
      .body(
        AskPayload(
          messages = List(
            Message(from = MessageFrom.User, message = "Hi").removeMetadata
          ),
          basedOnDocsOnly = true,
          scope = None
        ).asJson.toString
      )
      .send(backendStub)
      .map(_.code)
      .assertEquals(StatusCode.Ok)

  }
}
