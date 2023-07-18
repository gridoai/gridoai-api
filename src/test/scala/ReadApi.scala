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
import io.circe.generic.auto.*
import io.circe.syntax.*
import munit.CatsEffectSuite
import sttp.client3.UriContext
import sttp.client3.basicRequest
import sttp.client3.testing.SttpBackendStub
import sttp.model.Header
import sttp.model.StatusCode
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.server.ServerEndpoint

val mockedDocsResponse =
  """[{"documentUid":"694b8567-8c93-45c6-8051-34be4337e740","documentName":"Sky observations","documentSource":"https://www.nasa.gov/planetarydefense/faq/asteroid","uid":"694b8567-8c93-45c6-8051-34be4337e740","content":"The sky is blue","tokenQuantity":4}]"""

val authHeader = Header("Authorization", s"Bearer ${makeMockedToken}")
class API extends CatsEffectSuite {
  given db: DocDB[IO] = MockDocDB

  val searchDocsBE = serverStubOf(withService.searchDocs)

  test("health check should return OK") {

    val response = basicRequest
      .get(uri"http://test.com/health")
      .send(serverStubOf(withService.healthCheck))

    assertIO(response.map(_.body), Right("OK"))
  }
  test("Can't get documents without auth") {
    val responseWithoutAuth = basicRequest
      .get(uri"http://test.com/documents")
      .send(serverStubOf(withService.listDocs))

    assertIO(responseWithoutAuth.map(_.code), StatusCode.Unauthorized)
  }
  test("Can't create documents without auth") {
    val responseWithoutAuth = basicRequest
      .post(uri"http://test.com/documents")
      .send(serverStubOf(withService.createDocument))

    assertIO(responseWithoutAuth.map(_.code), StatusCode.Unauthorized)
  }
  test("Can't delete documents without auth") {
    val responseWithoutAuth = basicRequest
      .delete(uri"http://test.com/documents/123")
      .send(serverStubOf(withService.deleteDoc))

    assertIO(responseWithoutAuth.map(_.code), StatusCode.Unauthorized)
  }
  test("Can't search documents without auth") {
    val responseWithoutAuth = basicRequest
      .get(uri"http://test.com/search?query=foo&tokenLimit=1000")
      .send(searchDocsBE)

    assertIO(responseWithoutAuth.map(_.code), StatusCode.Unauthorized)
  }
  test("Searches for a chunk") {

    val authenticatedRequest = basicRequest
      .get(uri"http://test.com/search?query=foo&tokenLimit=1000")
      .headers(authHeader)
      .send(searchDocsBE)

    assertIO(
      authenticatedRequest
        .trace("document search response")
        .map(x =>
          println("THE DAMN BODY " + x.body)
          x.body
        ),
      Right(mockedDocsResponse)
    )
  }

  test("Creates a document") {
    val authenticatedRequest = basicRequest
      .post(uri"http://test.com/documents")
      .headers(authHeader)
      .body(
        DocumentCreationPayload(
          name = "Sky observations",
          source = "https://www.nasa.gov/planetarydefense/faq/asteroid",
          content = "The sky is blue"
        ).asJson.toString
      )
      .send(serverStubOf(withService.createDocument))

    assertIO(authenticatedRequest.map(_.code), StatusCode.Ok)
  }

  test("Ask LLM") {
    val backendStub =
      serverStubOf(
        withService.askLLM
      )

    basicRequest
      .post(uri"http://test.com/ask")
      .headers(authHeader)
      .body(
        List(Message(from = MessageFrom.User, message = "Hi")).asJson.toString
      )
      .send(backendStub)
      .map(_.code)
      .assertEquals(StatusCode.Ok)

  }
}
