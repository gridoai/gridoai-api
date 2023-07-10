package com.gridoai.test
import cats.effect.IO
import fs2.Stream
import fs2.text.utf8
import sttp.client3.testing.SttpBackendStub

import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.stub.TapirStubInterpreter

def streamToString(stream: Stream[IO, Byte]): IO[String] = {
  stream.through(utf8.decode).compile.toList.map(_.mkString)
}

val serverStub = TapirStubInterpreter(
  SttpBackendStub(new CatsMonadError[IO]())
)

def serverStubOf(endpoint: ServerEndpoint[Any, IO]) = {
  serverStub.whenServerEndpointRunLogic(endpoint).backend()
}
