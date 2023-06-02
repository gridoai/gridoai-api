package com.programandonocosmos.adapters
import cats.effect.IO
import sttp.client3._
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.model.StatusCode

val catsBackend =
  ((HttpClientCatsBackend
    .resource[IO]()
    .use(IO.pure)))

def sendRequest[T] = (r: Request[T, Any]) => catsBackend.flatMap(r.send)

extension [T](r: Request[T, Any]) def sendReq() = sendRequest(r)

class HttpClient(endpoint: String):
  def get(path: String) =
    basicRequest.get(uri"${endpoint + path}")

  def post(path: String) =
    basicRequest.post(uri"${endpoint + path}")
