import cats.effect.IO
import cats.effect.SyncIO
import com.gridoai.endpoints.*
import com.gridoai.models.DocDB
import com.gridoai.models.MockDocDB
import com.gridoai.utils.|>
import com.gridoai.domain.*
import fs2.Stream
import fs2.text.utf8Decode
import munit.CatsEffectSuite
import org.http4s.Method
import org.http4s.Request
import org.http4s.EntityBody
import org.http4s.implicits.uri
import org.http4s.circe.*

def streamToString(stream: Stream[IO, Byte]): IO[String] = {
  stream.through(utf8Decode).compile.toList.map(_.mkString)
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
        """[{"uid":"694b8567-8c93-45c6-8051-34be4337e740","name":"Sky observations","content":"The sky is blue","url":"https://www.nasa.gov/planetarydefense/faq/asteroid","numberOfWords":4}]"""
      )
  }

  test("Ask LLM") {
    routes.orNotFound
      .run(
        Request[IO](
          Method.POST,
          uri = uri"/ask"
        ).withEntity(List(Message(from = MessageFrom.User, message = "Hi")))
      )
      .map(_.body)
      .flatMap(streamToString)
      .assertEquals(
        """{}"""
      )
  }
}
