package com.gridoai.adapters.clerk

import com.gridoai.utils.*
import cats.effect.IO
import cats.implicits._
import com.gridoai.adapters.*
import com.gridoai.domain.*
import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.*
import io.circe.syntax.*
import sttp.model.{Header, MediaType}
import io.circe.derivation.Configuration
import io.circe.derivation.ConfiguredEnumCodec
import org.slf4j.LoggerFactory

val CLERK_ENDPOINT = "https://api.clerk.com/v1"
val CLERK_SECRET_KEY =
  sys.env.getOrElse("CLERK_SECRET_KEY", "")

case class PublicMetadata(
    googleDriveAccessToken: Option[String] = None,
    googleDriveRefreshToken: Option[String] = None,
    plan: Option[Plan] = None,
    customerId: Option[String] = None
)

case class User(
    id: String,
    public_metadata: PublicMetadata,
    email_addresses: List[EmailAddress]
)

case class UpdateUser(
    public_metadata: PublicMetadata
)

case class OrganizationMetadata(
    plan: Option[Plan] = None,
    customerId: String,
    googleDriveAccessToken: Option[String] = None,
    googleDriveRefreshToken: Option[String] = None
)

case class OrganizationMetadataUpdate(
    plan: Option[Plan] = None,
    customerId: Option[String] = None,
    googleDriveAccessToken: Option[String] = None,
    googleDriveRefreshToken: Option[String] = None
)
case class Organization(
    // `object`: String,
    id: String,
    name: String,
    // slug: String,
    // members_count: Int,
    // max_allowed_memberships: Int,
    // admin_delete_enabled: Boolean,
    public_metadata: OrganizationMetadata,
    // private_metadata: Any,
    created_by: String
    // created_at: Int,
    // updated_at: Int
)
case class UserCreatedData(
    // birthday: String,
    // created_at: Int,
    email_addresses: List[EmailAddress],
    // external_id: String,
    first_name: Option[String],
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

case class UpdateEmailAddress(verified: Boolean, primary: Boolean)

case class EmailAddress(
    email_address: String,
    id: String,
    verification: Verification
)

case class OrganizationMemberShipListData(
    id: String,
    `object`: String,
    role: String,
    // created_at: Int,
    // updated_at: Int,
    organization: Organization,
    public_user_data: PublicUserData
)

case class PublicUserData(
    user_id: String,
    first_name: String,
    last_name: String,
    // profile_image_url: String,
    // image_url: String,
    // has_image: Boolean,
    identifier: String
)

case class OrganizationMemberShipList(
    data: Seq[OrganizationMemberShipListData]
    // total_count: Int
)

case class UserCreated(
    data: UserCreatedData
)

case class CreateUser(
    phone_number: List[String],
    email_address: List[String],
    password: String
)

case class Verification(
    status: String,
    strategy: String
)
case class UpdateOrganization(
    public_metadata: Option[OrganizationMetadata] = None,
    max_allowed_memberships: Option[Int] = None
)

case class CreateOrganization(
    name: String,
    created_by: String,
    public_metadata: OrganizationMetadata,
    slug: Option[String] = None,
    max_allowed_memberships: Option[Int] = None
)
object SessionStatus:
  given Configuration =
    Configuration.default.withTransformConstructorNames(_.toLowerCase)

enum SessionStatus derives ConfiguredEnumCodec:
  case Active, Revoked, Ended, Expired, Removed, Abandoned, Replaced

case class Session(
    last_active_organization_id: Option[String],
    id: String,
    user_id: String,
    status: SessionStatus
)

case class ListUsers(
    email_address: List[String],
    limit: Option[Int] = None
)
case class DeletionResponse(
    `object`: String,
    id: String,
    deleted: Boolean
)
case class MergeAndUpdateUserMetadataPayload(
    public_metadata: PublicMetadata
)
case class MergeAndUpdateOrgMetadataPayload(
    public_metadata: OrganizationMetadataUpdate
)
object ClerkClient:
  val logger = LoggerFactory.getLogger(getClass.getName)

  val Http = HttpClient(CLERK_ENDPOINT)
  private val authHeader = Header("Authorization", s"Bearer $CLERK_SECRET_KEY")
  object user:
    def get(uid: String): IO[Either[String, User]] =
      Http
        .get(s"/users/$uid")
        .header(authHeader)
        .sendReq()
        .map(
          _.body.flatMap(
            decode[User](_).left.map(_.getMessage())
          )
        ) |> attempt

    def byEmail(email: String): IO[Either[String, User]] =
      user
        .listByEmail(email)
        .map(_.flatMap(_.headOption.toRight("No user found")))

    def byPhones(phoneNumbers: List[String]): IO[Either[String, User]] =
      user
        .listByPhones(phoneNumbers)
        .map(_.flatMap(_.headOption.toRight("No user found")))

    def getFirstOrgByUID(uid: String) =
      user
        .listMemberships(uid)
        .map(
          _.flatMap(_.data.headOption.toRight("No org found"))
        )

    def getOrgByCustomerId(email: String, customerId: String) =
      user
        .byEmail(email)
        .mapRight(_.id)
        .flatMapRight(user.listMemberships)
        .flatMapRight(
          _.data
            .find(_.organization.public_metadata.customerId == customerId) match
            case Some(membership) => IO.pure(Right(membership.organization))
            case None             => IO.pure(Left("No org found"))
        )

    def getPublicMetadata(
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

    def mergeAndUpdateMetadata(
        publicMetadata: PublicMetadata
    )(userId: String) =
      val body = MergeAndUpdateUserMetadataPayload(
        publicMetadata
      ).asJson.deepDropNullValues.noSpaces
      Http
        .patch(s"/users/$userId/metadata")
        .body(body)
        .header(authHeader)
        .contentType(MediaType.ApplicationJson)
        .sendReq()
        .map(
          _.body.flatMap(
            decode[User](_).left.map(_.getMessage())
          )
        )

    def updateEmailAddress(
        emailId: String,
        verified: Boolean,
        primary: Boolean
    ): IO[Either[String, UpdateEmailAddress]] =
      val body = UpdateEmailAddress(verified, primary).asJson.noSpaces
      Http
        .patch(s"/email_addresses/$emailId")
        .body(body)
        .header(authHeader)
        .contentType(MediaType.ApplicationJson)
        .sendReq()
        .map(
          _.body.flatMap(
            decode[UpdateEmailAddress](_).left.map(_.getMessage())
          )
        )

    def listByEmail(
        emailAddress: String,
        limit: Int = 1
    ): IO[Either[String, List[User]]] =
      Http
        .get(s"/users?email_address=$emailAddress&limit=$limit")
        .header(authHeader)
        .contentType(MediaType.ApplicationJson)
        .sendReq()
        .map(
          _.body.flatMap(
            decode[List[User]](_).left.map(_.getMessage())
          )
        ) |> attempt

    def listByPhones(
        phoneNumbers: List[String],
        limit: Int = 1
    ): IO[Either[String, List[User]]] =
      Http
        .get(
          s"/users?limit=$limit&phone_number=${phoneNumbers.mkString("&phone_number=")}"
        )
        .header(authHeader)
        .contentType(MediaType.ApplicationJson)
        .sendReq()
        .map(
          _.body.flatMap(
            decode[List[User]](_).left.map(_.getMessage())
          )
        ) |> attempt

    def listMemberships(
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

    def create(
        phoneNumber: String,
        email: String,
        password: String
    ): IO[Either[String, UserCreatedData]] =
      val body =
        CreateUser(List(phoneNumber), List(email), password).asJson.noSpaces
      logger.info("Creating user... ")
      Http
        .post(s"/users")
        .body(body)
        .header(authHeader)
        .contentType(MediaType.ApplicationJson)
        .sendReq()
        .map(
          _.body.flatMap(
            decode[UserCreatedData](_).left.map(_.getMessage())
          )
        ) |> attempt

  object session:

    def list(
        userId: String,
        limit: Int = 10,
        offset: Int = 0
    ): IO[Either[String, List[Session]]] =
      Http
        .get(s"/sessions?limit=${limit}&offset=${offset}&user_id=${userId}")
        .header(authHeader)
        .sendReq()
        .map(
          _.body.flatMap(
            decode[List[Session]](_).left.map(_.getMessage())
          )
        ) |> attempt
  object org:
    def delete(orgId: String) =
      Http
        .delete(s"/organizations/$orgId")
        .header(authHeader)
        .sendReq()
        .map(
          _.body.flatMap(
            decode[DeletionResponse](_).left.map(_.getMessage())
          )
        ) |> attempt

    def get(orgId: String) =
      Http
        .get(s"/organizations/$orgId")
        .header(authHeader)
        .sendReq()
        .map(
          _.body.flatMap(
            decode[Organization](_).left.map(_.getMessage())
          )
        ) |> attempt

    def upsert(
        name: String,
        plan: Plan,
        customerId: String,
        preUpdate: (
            memberShip: OrganizationMemberShipListData
        ) => IO[Either[Any, Any]] = _ => IO(Right(()))
    )(
        by: String
    ): IO[Either[String, Organization]] =
      user.listMemberships(by).flatMapRight {
        _.data.find(m => m.organization.name == name) match
          case Some(membership) =>
            logger.info("Found membership, updating org...")
            preUpdate(membership) !> updatePlan(
              membership.organization.id,
              plan,
              (customerId)
            )
          case None =>
            logger.info("No membership found, creating org...")
            create(name, plan, customerId)(by)
      }

    def create(
        name: String,
        plan: Plan,
        customerId: String
    )(
        by: String
    ): IO[Either[String, Organization]] =
      val body = CreateOrganization(
        name = name,
        created_by = by,
        public_metadata = (OrganizationMetadata(
          plan = Some(plan),
          customerId = customerId
        )),
        max_allowed_memberships = getMaxUsersByPlan(plan)
      ).asJson.noSpaces
      logger.info("Creating org... ")
      logger.info(body)
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

    def mergeAndUpdateMetadata(
        organizationId: String,
        plan: Option[Plan] = None,
        customerId: Option[String] = None,
        googleDriveAccessToken: Option[String] = None,
        googleDriveRefreshToken: Option[String] = None
    ): IO[Either[String, Organization]] =
      val body = MergeAndUpdateOrgMetadataPayload(
        public_metadata = OrganizationMetadataUpdate(
          plan,
          customerId,
          googleDriveAccessToken,
          googleDriveRefreshToken
        )
      ).asJson.deepDropNullValues.noSpaces
      Http
        .patch(s"/organizations/$organizationId/metadata")
        .body(body)
        .header(authHeader)
        .contentType(MediaType.ApplicationJson)
        .sendReq()
        .map(
          _.body.flatMap(
            decode[Organization](_).left.map(_.getMessage())
          )
        )
        |> attempt

    def updatePlan(
        organizationId: String,
        plan: Plan,
        customerId: String
    ): IO[Either[String, Organization]] =
      val body = UpdateOrganization(
        max_allowed_memberships = getMaxUsersByPlan(plan)
      ).asJson.noSpaces
      val req1 = mergeAndUpdateMetadata(
        organizationId,
        plan = Some(plan),
        customerId = Some(customerId)
      )
      val req2 = Http
        .patch(s"/organizations/$organizationId")
        .body(body)
        .header(authHeader)
        .contentType(MediaType.ApplicationJson)
        .sendReq()
        .map(
          _.body.flatMap(
            decode[Organization](_).left.map(_.getMessage())
          )
        ) |> attempt
      List(req1, req2).parSequence.map(_.last)

  def setGDriveMetadata(userId: String)(
      googleDriveAccessToken: String,
      googleDriveRefreshToken: String
  ): IO[Either[String, (String, String)]] =
    logger.info("Sending tokens to Clerk...")

    user
      .mergeAndUpdateMetadata(
        PublicMetadata(
          Some(googleDriveAccessToken),
          Some(googleDriveRefreshToken)
        )
      )(userId)
      .mapRight: user =>
        (
          user.public_metadata.googleDriveAccessToken,
          user.public_metadata.googleDriveRefreshToken
        )
      .map(_.flatMap:
        case (Some(x), Some(y)) =>
          logger.info("Tokens sent!")
          Right((x, y))
        case _ =>
          Left("Some information was not set")
      ) |> attempt
