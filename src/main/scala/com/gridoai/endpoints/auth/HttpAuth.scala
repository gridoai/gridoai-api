package com.gridoai.endpoints.auth

import pdi.jwt._
import io.circe.parser.decode
import io.circe.generic.semiauto.deriveDecoder
import io.circe.Decoder
import com.gridoai.auth.JWTPayload
import sttp.model.StatusCode
import sttp.tapir._
import cats.effect.IO
import com.gridoai.utils.|>
import com.gridoai.utils.trace
import com.gridoai.utils.addLocationToLeft
implicit val jwtPayloadDecoder: Decoder[JWTPayload] = deriveDecoder[JWTPayload]
case class AuthError(error: String)

def decodeJwt(token: String) =
  Jwt
    .decode(token, JwtOptions(signature = false))
    .toEither
    .map(claim => claim.content)
    .trace
    .flatMap(decode[JWTPayload](_))
    .left
    .map(_.getMessage())
    .addLocationToLeft
    .trace
    |> IO.pure

private val securedWithBearerEndpoint = endpoint
  .securityIn(auth.bearer[String]())
  .errorOut(statusCode(StatusCode.Unauthorized))

val securedWithBearer =
  securedWithBearerEndpoint
    .errorOut(stringBody)
    .serverSecurityLogic(decodeJwt)
