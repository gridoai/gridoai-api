package com.gridoai.adapters
import cats.effect.IO
import sttp.client3._
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.model.StatusCode

import java.net.InetAddress
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

val catsBackend =
  HttpClientCatsBackend
    .resource[IO]()
    .use(IO.pure)

def sendRequest[T] = (r: Request[T, Any]) => catsBackend.flatMap(r.send)

extension [T](r: Request[T, Any]) def sendReq() = sendRequest(r)

def isHostReachable(
    host: String
) =
  InetAddress.getByName(host).isReachable(1000)

class HttpClient(endpoint: String):

  def get(path: String) =
    basicRequest.get(uri"${endpoint + path}")

  def post(path: String) =
    basicRequest.post(uri"${endpoint + path}")
