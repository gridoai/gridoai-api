package com.gridoai.auth
import io.circe._
import io.circe.generic.semiauto._

case class JWTPayload(
    azp: String,
    iss: String,
    orgId: Option[String],
    role: Option[String],
    sid: String,
    sub: String
)

val mockedJwt = JWTPayload("", "", Some("Incredible org"), None, "", "")
