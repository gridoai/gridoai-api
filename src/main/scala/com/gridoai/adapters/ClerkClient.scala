package com.gridoai.adapters

import com.gridoai.utils.*
import cats.effect.IO
import com.gridoai.adapters.*
import com.gridoai.domain.*
import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.*
import io.circe.syntax.*
import cats.implicits.*
import sttp.model.{Header, MediaType}

val CLERK_ENDPOINT = "https://api.clerk.com/v1"
val CLERK_SECRET_KEY =
  sys.env.getOrElse("CLERK_SECRET_KEY", "")

case class PublicMetadata(
    googleDriveAccessToken: Option[String],
    googleDriveRefreshToken: Option[String]
)

case class User(
    id: String,
    public_metadata: PublicMetadata
)

case class UpdateUser(
    public_metadata: PublicMetadata
)

object ClerkClient:

  val Http = HttpClient(CLERK_ENDPOINT)
  private val authHeader = Header("Authorization", s"Bearer $CLERK_SECRET_KEY")

  def getUserPublicMetadata(
      userId: String
  ): IO[Either[String, PublicMetadata]] =
    Http
      .get(s"/users/$userId")
      .header(authHeader)
      .sendReq()
      .map(
        _.body.flatMap(
          decode[User](_).left.map(_.getMessage())
        )
      )
      .mapRight(
        _.public_metadata
      ) |> attempt

  def setUserPublicMetadata(userId: String)(
      googleDriveAccessToken: String,
      googleDriveRefreshToken: String
  ): IO[Either[String, (String, String)]] =
    println("Sending tokens to Clerk...")
    val body = UpdateUser(
      public_metadata = PublicMetadata(
        Some(googleDriveAccessToken),
        Some(googleDriveRefreshToken)
      )
    ).asJson.noSpaces
    Http
      .patch(s"/users/$userId")
      .body(body)
      .header(authHeader)
      .contentType(MediaType.ApplicationJson)
      .sendReq()
      .map(
        _.body.flatMap(
          decode[User](_).left.map(_.getMessage())
        )
      )
      .mapRight: user =>
        (
          user.public_metadata.googleDriveAccessToken,
          user.public_metadata.googleDriveRefreshToken
        )
      .map(_.flatMap:
        case (Some(x), Some(y)) =>
          println("Tokens sent!")
          Right((x, y))
        case _ =>
          Left("Some information was not set")
      ) |> attempt
