import cats.effect.IO
import com.gridoai.domain.*
import com.gridoai.endpoints.*
import com.gridoai.models.DocDB
import com.gridoai.models.MockDocDB
import fs2.Stream
import io.circe.generic.auto.*
import munit.CatsEffectSuite

import org.http4s.EntityEncoder
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.circe.*
import org.http4s.implicits.uri
import fs2.text.utf8

def streamToString(stream: Stream[IO, Byte]): IO[String] = {
  stream.through(utf8.decode).compile.toList.map(_.mkString)
}

class ExampleSuite extends CatsEffectSuite {
  given db: DocDB[IO] = MockDocDB

  test("Health check returns OK") {
    routes.orNotFound
      .run(
        Request(
          Method.GET,
          uri = uri"/health"
        )
      )
      .map(_.body)
      .flatMap(streamToString)
      .assertEquals("OK")

  }

  test("Searches for a document") {
    routes.orNotFound
      .run(
        Request(
          Method.GET,
          uri = uri"/search?query=foo"
        )
      )
      .map(_.body)
      .flatMap(streamToString)
      .assertEquals(
        """[{"uid":"694b8567-8c93-45c6-8051-34be4337e740","name":"Sky observations","content":"The sky is blue","url":"","numberOfWords":0}]"""
      )
  }

  test("Ask LLM") {
    given listMessageEncoder: EntityEncoder[IO, List[Message]] =
      jsonEncoderOf[IO, List[Message]]

    routes.orNotFound
      .run(
        Request[IO](
          Method.POST,
          uri = uri"/ask"
        ).withEntity(List(Message(from = MessageFrom.User, message = "Hi")))
      )
      .map(_.status)
      .assertEquals(Status.Ok)
  }
}
