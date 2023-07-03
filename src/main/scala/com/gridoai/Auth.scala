package com.gridoai.auth
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import java.time.Clock
import pdi.jwt.{Jwt, JwtAlgorithm}
import com.gridoai.utils.trace

implicit val clock: Clock = Clock.systemUTC
case class JWTPayload(
    azp: String,
    iss: String,
    orgId: Option[String],
    role: Option[String],
    sid: String,
    sub: String
)

val mockedJwt =
  JWTPayload(
    "www.grioai.com",
    "accounts.mock.com",
    Some("org1"),
    Some("member"),
    "session_blablablabl",
    "user_blablablabla"
  )

def makeMockedToken =
  Jwt
    .encode(
      mockedJwt.asJson.toString,
      sys.env.get("JWT_SECRET").getOrElse(("")),
      JwtAlgorithm.HS256
    )
    .trace

val getOrgIdAndRolesFromJwt = (jwt: JWTPayload) =>
  (jwt.orgId.getOrElse(jwt.sub), jwt.role.getOrElse("member"))

def limitRole[A](role: Option[String], error: A)(resource: A) =
  role match
    case Some("admin") => resource
    case _             => error

def authErrorMsg(author: Option[String] = Some("User")) =
  author match
    case Some(author) =>
      s"${author} isn't authorized to perform this action"
    case None =>
      "User isn't authorized to perform this action"
