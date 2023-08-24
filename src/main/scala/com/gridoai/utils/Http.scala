package com.gridoai.utils

import java.net.http.HttpRequest
import java.net.URI
import collection.convert.ImplicitConversions.`map AsScala`
import collection.convert.ImplicitConversions.`list asScalaBuffer`

def changeUri(
    makeNewURI: ((oldUri: URI) => URI)
)(request: HttpRequest): HttpRequest = {
  println("CHANGING THE DAMN REQUEST ----------")
  println(request)
  try {

    val oldUri = request.uri()
    val newUri = makeNewURI(oldUri)
    println("NEW URI: " + newUri)

    val newReq = HttpRequest
      .newBuilder(newUri)
      .method(request.method(), request.bodyPublisher().orElse(null))
      .headers(
        request.headers().map().flatMap(e => Seq(e._1, e._2.head)).toArray: _*
      )
      .headers("api-key", sys.env.get("OPENAI_API_KEY").get)
      .timeout(request.timeout().orElse(null))
      .build()

    print("CHANGED REQUEST: ")
    println((newReq.uri(), newReq.headers(), newReq.method()))
    newReq
  } catch {
    case e: Exception =>
      println("Error: " + e)
      request
  }

}
