package com.gridoai.adapters
import cats.effect.IO
import sttp.client3._
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import java.net.InetAddress
import scala.concurrent.duration.Duration
import concurrent.duration.DurationInt
import cats.effect.unsafe.implicits.global
import com.gridoai.utils.changeUri

val catsBackend =
  HttpClientCatsBackend
    .resource[IO]()
    .use(IO.pure)

import java.net.URI
def makeAzureURIOfOpenAI(originalURI: URI): URI = {
  // https://gridoai.openai.azure.com/openai/deployments/API/completions?api-version=2023-03-15-preview
  val newPath =
    s"/openai/deployments/API/${originalURI.getPath.replaceAll("/v1", "").stripPrefix("/")}"
  val newURI = new URI(
    originalURI.getScheme,
    null,
    "gridoai.openai.azure.com",
    originalURI.getPort,
    newPath,
    "api-version=2023-03-15-preview",
    null
  )
  newURI
}

val openAiClientBackend = HttpClientCatsBackend
  .resource[IO](customizeRequest = sys.env.get("OPENAI_ENDPOINT") match
    case None        => identity
    case Some(value) => changeUri(makeAzureURIOfOpenAI)
  )
  .use(IO.pure)
  .unsafeRunSync()

def sendRequest[T] = (r: Request[T, Any]) => catsBackend.flatMap(r.send)

extension [T](r: Request[T, Any])
  def sendReq(
      retries: Int = 1,
      retryDelay: Duration = 0.seconds
  ): IO[Response[T]] =

    def retry(delay: Duration = retryDelay) =
      IO.println(s"Retrying request: ${r.uri}")
        >> IO.sleep(delay)
        >> sendReq(
          retries - 1
        )

    if retries > 0 then
      sendRequest(r)
        .flatMap(response => {
          response
            .header("Retry-After")
            .flatMap(_.toIntOption)
            .map(_.seconds) match
            case Some(delay) =>
              println(s"Retrying request: ${r.uri}")
              retry(delay)
            case None => IO.pure(response)
        })
        .recoverWith:
          case e =>
            println(s"Retrying request: ${r.uri}")
            retry()
    else sendRequest(r)

def isHostReachable(
    host: String
) =
  InetAddress.getByName(host).isReachable(1000)

case class HttpClient(endpoint: String):

  def get(path: String) =
    basicRequest.get(uri"${endpoint + path}")

  def post(path: String) =
    basicRequest.post(uri"${endpoint + path}")

  def patch(path: String) =
    basicRequest.patch(uri"${endpoint + path}")
