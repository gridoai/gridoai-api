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
    last_name: Option[String]
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

case class OrganizationMemberShipListData(
    id: String,
    `object`: String,
    role: String,
    created_at: Int,
    updated_at: Int,
    organization: Organization,
    public_user_data: PublicUserData
)

case class PublicUserData(
    user_id: String,
    first_name: String,
    last_name: String,
    profile_image_url: String,
    image_url: String,
    has_image: Boolean,
    identifier: String
)

case class OrganizationMemberShipList(
    data: Seq[OrganizationMemberShipListData],
    total_count: Int
)

case class UserCreated(
    data: UserCreatedData
)

case class Verification(
    status: String,
    strategy: String
)
case class UpdateOrganization(
    public_metadata: OrganizationMetadata,
    max_allowed_memberships: Option[Int] = None
)

case class CreateOrganization(
    name: String,
    created_by: String,
    public_metadata: OrganizationMetadata,
    slug: Option[String] = None,
    max_allowed_memberships: Option[Int] = None
)

import Plan._

def getMaxUsersByPlan: Plan => Option[Int] =
  case Free       => Some(1)
  case Starter    => Some(3)
  case Pro        => Some(10)
  case Enterprise => None

object ClerkClient:

  val Http = HttpClient(CLERK_ENDPOINT)
  private val authHeader = Header("Authorization", s"Bearer $CLERK_SECRET_KEY")

  def listMembershipsOfUser(
      userId: String
  ): IO[Either[String, OrganizationMemberShipList]] =
    Http
      .get(s"/users/$userId/organization_memberships")
      .header(authHeader)
      .sendReq()
      .map(
        _.body.flatMap(
          decode[OrganizationMemberShipList](_).left.map(_.getMessage())
        )
      ) |> attempt

  def deleteOrg(orgId: String) =
    Http
      .delete(s"/organizations/$orgId")
      .header(authHeader)
      .sendReq()
      .map(
        _.body.flatMap(
          decode[Organization](_).left.map(_.getMessage())
        )
      ) |> attempt

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

  def createOrg(
      by: String,
      name: String,
      plan: Plan
  ): IO[Either[String, Organization]] =
    val body = CreateOrganization(
      name = name,
      created_by = by,
      public_metadata = (OrganizationMetadata(plan)),
      max_allowed_memberships = getMaxUsersByPlan(plan)
    ).asJson.noSpaces
    Http
      .post("/organizations")
      .body(body)
      .header(authHeader)
      .contentType(MediaType.ApplicationJson)
      .sendReq()
      .map(
        _.body.flatMap(
          decode[Organization](_).left.map(_.getMessage())
        )
      ) |> attempt

  def updateOrganizationPlan(
      organizationId: String,
      plan: Plan
  ): IO[Either[String, Unit]] =
    val body = UpdateOrganization(
      public_metadata = OrganizationMetadata(plan),
      max_allowed_memberships = getMaxUsersByPlan(plan)
    ).asJson.noSpaces
    Http
      .patch(s"/organizations/$organizationId")
      .body(body)
      .header(authHeader)
      .contentType(MediaType.ApplicationJson)
      .sendReq()
      .map(
        _.body.flatMap(
          decode[Organization](_).left.map(_.getMessage())
        )
      )
      .mapRight(_ => ()) |> attempt

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
