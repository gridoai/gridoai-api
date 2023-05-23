package com.programandonocosmos

import com.google.cloud.functions.{HttpFunction, HttpRequest, HttpResponse}

// [START functions_api_get]
class ScalaHttpFunction extends HttpFunction {
  override def service(
      httpRequest: HttpRequest,
      httpResponse: HttpResponse
  ): Unit = {
    httpResponse.getWriter.write("Hello World!")
  }
}
// [END functions_api_get]
