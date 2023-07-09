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

def streamToString(stream: Stream[IO, Byte]): IO[String] = {
  stream.through(utf8.decode).compile.toList.map(_.mkString)
}

val serverStub = TapirStubInterpreter(
  SttpBackendStub(new CatsMonadError[IO]())
)

def serverStubOf(endpoint: ServerEndpoint[Any, IO]) = {
  serverStub.whenServerEndpointRunLogic(endpoint).backend()
}
