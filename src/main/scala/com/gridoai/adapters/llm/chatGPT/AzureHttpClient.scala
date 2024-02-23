package com.gridoai.adapters.llm.chatGPT

import com.azure.core.http.{HttpRequest, HttpMethod, HttpClient}
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder
import com.azure.core.util.Context
import io.netty.handler.codec.http.HttpHeaderNames
import reactor.core.publisher.Mono
import com.azure.core.http.HttpResponse
import com.gridoai.utils.getEnv

class CustomHttpClient(originalClient: HttpClient, baseUrlOverride: String)
    extends HttpClient {

  override def send(
      request: HttpRequest
  ): reactor.core.publisher.Mono[com.azure.core.http.HttpResponse] = {
    // Modify the request with the new base URL
    val modifiedRequest =
      request.setUrl(
        baseUrlOverride + request.getUrl.getPath.replace("/v1", "")
      )

    // Delegate the actual sending to the original client
    originalClient.send(modifiedRequest)
  }
}
val originalClient = new NettyAsyncHttpClientBuilder().build()
val customClient =
  new CustomHttpClient(originalClient, getEnv("OPENAI_ENDPOINT"))
