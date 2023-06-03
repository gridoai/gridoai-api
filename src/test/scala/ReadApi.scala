import cats.effect.IO
import cats.effect.SyncIO
import com.programandonocosmos.endpoints.*
import com.programandonocosmos.models.DocDB
import com.programandonocosmos.models.MockDocDB
import com.programandonocosmos.utils.|>
import fs2.Stream
import fs2.text.utf8Decode
import munit.CatsEffectSuite
import org.http4s.Method
import org.http4s.Request
import org.http4s.implicits.uri

def streamToString(stream: Stream[IO, Byte]): IO[String] = {
  stream.through(utf8Decode).compile.toList.map(_.mkString)
}

class ExampleSuite extends CatsEffectSuite {
  given db: DocDB[IO] = MockDocDB

  test("Health check returns OK") {
    endpoints.orNotFound
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
    endpoints.orNotFound
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
}
