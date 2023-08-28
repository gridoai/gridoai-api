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

import io.circe.Codec
import io.circe.derivation.Configuration

given Configuration = Configuration.default
  .withDiscriminator("type")
  .withTransformConstructorNames(_.toLowerCase())

enum Plan:
  case Free, Starter, Pro, Enterprise
object Plan:
  given Codec[Plan] = Codec.AsObject.derivedConfigured

case class OrganizationMetadata(
    plan: Plan
)
case class Organization(
    `object`: String,
    id: String,
    name: String,
    slug: String,
    members_count: Int,
    max_allowed_memberships: Int,
    admin_delete_enabled: Boolean,
    public_metadata: OrganizationMetadata,
    // private_metadata: Any,
    created_by: String,
    created_at: Int,
    updated_at: Int
)
case class UserCreatedData(
    // birthday: String,
    // created_at: Int,
    email_addresses: List[EmailAddress],
    // external_id: String,
    first_name: String,
    // gender: String,
    id: String,
    // image_url: String,
    last_name: String
    // last_sign_in_at: Int,
    // password_enabled: Boolean,
    // primary_email_address_id: String,
    // primary_phone_number_id: String,
    // primary_web3_wallet_id: String,
    // profile_image_url: String,
    // two_factor_enabled: Boolean,
    // updated_at: Int,
    // username: String
)

case class EmailAddress(
    email_address: String
    // id: String,
    // verification: Verification
)

case class UserCreated(
    data: UserCreatedData
)

case class Verification(
    status: String,
    strategy: String
)
case class UpdateOrganization(
    public_metadata: OrganizationMetadata
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

  // Update an organization
  //   PATH PARAMETERS
  // organization_id
  // required
  // string
  // The ID of the organization to update

  // REQUEST BODY SCHEMA: application/json
  // required
  // public_metadata
  // object
  // Metadata saved on the organization, that is visible to both your frontend and backend.

  // private_metadata
  // object
  // Metadata saved on the organization that is only visible to your backend.

  // name
  // string or null
  // The new name of the organization

  // slug
  // string or null
  // The new slug of the organization, which needs to be unique in the instance

  // max_allowed_memberships
  // integer or null
  // The maximum number of memberships allowed for this organization

  // admin_delete_enabled
  // boolean or null
  // If true, an admin can delete this organization with the Frontend API.

  // def updateOrganizationPlan(
  //     organizationId: String,
  //     plan: Plan
  // ): IO[Either[String, Unit]] =
  //   val body = ??? // UpdateOrganization().asJson.noSpaces
  //   Http
  //     .patch(s"/organizations/$organizationId")
  //     .body(body)
  //     .header(authHeader)
  //     .contentType(MediaType.ApplicationJson)
  //     .sendReq()
  //     .map(
  //       _.body.flatMap(
  //         decode[Organization](_).left.map(_.getMessage())
  //       )
  //     )
  //     .mapRight(_ => ()) |> attempt

  def setGDriveMetadata(userId: String)(
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
