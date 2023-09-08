package com.gridoai.adapters.payments

import com.gridoai.adapters.UserCreated

import com.stripe.net.Webhook

import com.stripe._, param.CustomerCreateParams

import java.util.HashMap
import cats.effect.kernel.Sync
import cats.implicits.*

import com.stripe.model.Customer
import com.gridoai.utils.attempt

import com.gridoai.adapters.ClerkClient
import com.gridoai.adapters.ClerkClient._

import com.stripe.model.Subscription
import com.stripe.model.StripeObject
import com.gridoai.domain.Plan
import cats.effect.IO
import com.gridoai.utils.*
import collection.JavaConverters.collectionAsScalaIterableConverter
import com.gridoai.adapters.PublicMetadata
import com.gridoai.adapters.ClerkClient.user.getActiveOrgByEmail

import com.stripe.param.billingportal.SessionCreateParams
import com.stripe.model.billingportal.Session
import com.gridoai.adapters.OrganizationMemberShipListData

import cats.Monad
import cats.data.EitherT

val stripeKey = sys.env
  .get("STRIPE_SECRET_KEY")
  .getOrElse(throw new Exception("STRIPE_SECRET_KEY not found"))

val STRIPE_WEBHOOK_KEY = sys.env
  .get("STRIPE_WEBHOOK_KEY")
  .getOrElse(throw new Exception("STRIPE_WEBHOOK_KEY not found"))

val client =
  Stripe.apiKey = stripeKey
  com.stripe.StripeClient(stripeKey)

val starterPlanId = sys.env
  .get("STRIPE_STARTER_PLAN_ID")
  .getOrElse(throw new Exception("STRIPE_STARTER_PLAN_ID not found"))

val proPlanId = sys.env
  .get("STRIPE_PRO_PLAN_ID")
  .getOrElse(throw new Exception("STRIPE_PRO_PLAN_ID not found"))

val individualPlanId = sys.env
  .get("STRIPE_INDIVIDUAL_PLAN_ID")
  .getOrElse(throw new Exception("STRIPE_INDIVIDUAL_PLAN_ID not found"))

val enterprisePlanId = sys.env
  .get("STRIPE_ENTERPRISE_PLAN_ID")
  .getOrElse(throw new Exception("STRIPE_ENTERPRISE_PLAN_ID not found"))

inline def getPlanById: String => Plan = {
  case p if p == starterPlanId    => Plan.Starter
  case p if p == proPlanId        => Plan.Pro
  case p if p == individualPlanId => Plan.Individual
  case p if p == enterprisePlanId => Plan.Enterprise
  case _                          => Plan.Free
}

def createCustomerFromClerkPayload[F[_]](
    payload: UserCreated
)(using F: Sync[F]) =
  Sync[F].blocking:
    CustomerCreateParams
      .builder()
      .setEmail(payload.data.email_addresses.head.email_address)
      .setName(
        payload.data.first_name + " " + payload.data.last_name.getOrElse("")
      )
      .setMetadata(
        new HashMap[String, String]() {
          put("clerkId", payload.data.id)
        }
      )
      .build()
      |> client.customers().create

def handleCreateCustomer[F[_]](
    payload: UserCreated,
    authorization: String
)(using F: Sync[F]): F[Either[String, String]] =

  println("Creating customer...")
  println((authorization, sys.env.get("WEBHOOK_KEY")))
  sys.env.get("WEBHOOK_KEY") match
    case Some(key) if key == authorization =>
      createCustomerFromClerkPayload(payload)
        .map(_.toJson)
        .attempt |> attempt
    case _ =>
      "Unauthorized".asLeft.pure[F]

def createCustomerPortalSession(customerId: String, baseUrl: String) =
  Sync[IO].blocking {
    val params = SessionCreateParams
      .builder()
      .setCustomer(customerId)
      .setReturnUrl(baseUrl)
      .build();

    val session = Session.create(params);
    session.getUrl()
  }.attempt |> attempt

def deleteCustomerUnsafe[F[_]](oldCustomerId: String)(using Sync[F]) =
  Sync[F].blocking {
    client.customers().delete(oldCustomerId)
  }.attempt |> attempt

def deleteCustomer[F[_]](
    oldCustomerId: String,
    maybeNewCustomerID: Option[String]
)(using Sync[F]): F[Either[String, String]] =
  maybeNewCustomerID match
    case None =>
      deleteCustomerUnsafe[F](oldCustomerId).mapRight(_ => oldCustomerId)
    case Some(value) => deleteCustomer[F](oldCustomerId, value)

def deleteCustomer[F[_]](oldCustomerId: String, newCustomerID: String)(using
    SyncInstance: Sync[F]
): F[Either[String, String]] =
  if oldCustomerId != newCustomerID then
    deleteCustomerUnsafe[F](oldCustomerId).mapRight(_ => newCustomerID)
  else SyncInstance.pure(Right(newCustomerID))

def deleteCustomerOfMembership[F[_]](newCustomerID: String)(
    membership: OrganizationMemberShipListData
)(using Sync[F]) =
  deleteCustomer(
    membership.organization.public_metadata.customerId,
    newCustomerID
  )

def fetchPlanOfSubscription[F[_]](
    subscriptionId: String
)(using Sync[F]) = Sync[F]
  .blocking {
    Subscription.retrieve(subscriptionId)
  }
  .attempt
  .map(_.flatMap(getSubscriptionPlan(_).toRight("No plan found"))) |> attempt

def handleCheckoutCompleted(
    eventObj: StripeObject
) =
  val session = eventObj.asInstanceOf[com.stripe.model.checkout.Session]
  val planPromise = fetchPlanOfSubscription[IO](session.getSubscription)

  val orgNameField = session.getCustomFields.asScala.find: field =>
    val key = field.getKey
    key == "nomedaorganizao" || key == "organizationname"
  val customerId = session.getCustomer

  val necessaryData = (
    Option(session.getClientReferenceId()),
    Option(session.getCustomerDetails.getEmail),
    orgNameField.map(_.getText.getValue),
  )

  necessaryData match
    case (None, Some(email), Some(orgName)) =>
      println("Got no client reference id, fetching by email...")
      planPromise.flatMapRight: plan =>
        user
          .byEmail(email)
          .mapRight(_.id)
          .flatMapRight(
            org.upsert(
              orgName,
              plan,
              customerId,
              preUpdate = deleteCustomerOfMembership[IO](customerId)
            )
          )

    case (Some(id), Some(email), Some(orgName)) =>
      planPromise
        .flatMapRight: plan =>
          (org.upsert(
            orgName,
            plan,
            customerId,
            preUpdate = deleteCustomerOfMembership[IO](customerId)
          )(id))

    case (Some(clientId), Some(email), None) =>
      user.mergeAndUpdateMetadata(
        PublicMetadata(
          plan = Some(Plan.Individual),
          customerId = Some(customerId)
        )
      )(clientId)
    case _ => IO(Left(s"Missing data: ${necessaryData}"))

def cancelOrgSubscriptionByEmail(email: String) =
  getActiveOrgByEmail(email)
    .mapRight(_.id)
    .flatMapRight(org.mergeAndUpdateMetadata(_, plan = (Some(Plan.Free))))

def cancelUserPlan =
  user.mergeAndUpdateMetadata(
    PublicMetadata(
      plan = Some(Plan.Free)
    )
  )

def cancelUserPlanByEmail(email: String) =
  user.byEmail(email).mapRight(_.id).flatMapRight(cancelUserPlan)

def cancelPlanByMail(email: String, plan: Plan) =
  println(s"Cancelling plan ${plan} for ${email}")
  plan match
    case Plan.Individual => cancelUserPlanByEmail(email)
    case Plan.Free       => IO(Left("Cannot cancel free plan"))
    case _               => cancelOrgSubscriptionByEmail(email)

def getValueFromOptionOrIO[T, L](
    option: Option[T],
    io: IO[Either[L, T]],
    noneValue: L
): IO[Either[L, T]] =
  option match
    case Some(value) => IO.pure(Right(value))
    case None        => io.mapLeft(_ => noneValue)

def upgradePlan(
    email: String,
    plan: Plan,
    newCustomerId: Option[String],
    maybeClientId: Option[String]
) =
  println(s"Upgrading plan to ${plan} for ${email} with ${newCustomerId}")

  plan match
    case (Plan.Individual) =>
      user
        .byEmail(email)
        .flatMapRight: user =>
          IO(user.public_metadata.customerId.toRight("No customer id found"))
            .flatMapRight(deleteCustomer[IO](_, newCustomerId))
            .mapRight(_ => user.id)
        .flatMapRight(
          user.mergeAndUpdateMetadata(
            PublicMetadata(
              plan = Some(plan),
              customerId = newCustomerId
            )
          )
        )

    case (Plan.Free) => IO(Left("Cannot upgrade to free plan"))
    case _ =>
      (for
        orgData <- EitherT(getActiveOrgByEmail(email))
        oldCustomerId = orgData.public_metadata.customerId
        _ <- EitherT(deleteCustomer[IO](oldCustomerId, newCustomerId))
        _ <- EitherT(org.updatePlan(orgData.id, plan, oldCustomerId))
      yield ()).value

def getSubscriptionPlan(sub: Subscription) =
  Option(sub.getItems.getData.get(0).getPlan.getProduct)
    .map(getPlanById)

def handleSubscriptionUpdate(eventObj: StripeObject): IO[Either[String, Any]] =
  val subscription = eventObj.asInstanceOf[Subscription]
  val customerId = subscription.getCustomer
  val customer = Customer.retrieve(customerId)

  val necessaryData = (
    Option(subscription.getCancelAt),
    Option(customer.getEmail),
    Option(subscription.getItems.getData.get(0).getPlan.getProduct),
  )
  println(
    s"Got necessary data: ${necessaryData} in event handleSubscriptionUpdate"
  )

  necessaryData match
    case (None, Some(email), Some(productId)) =>
      val plan = getPlanById(productId)

      upgradePlan(
        email,
        plan,
        Some(customerId),
        None
      )

    case (Some(_), Some(email), Some(productId)) =>
      cancelPlanByMail(email, getPlanById(productId))
    case _ => IO(Left(s"Missing data: ${necessaryData}"))

def handleDeleted(
    eventObj: StripeObject
) =
  val subscription = eventObj.asInstanceOf[Subscription]
  val customer = Customer.retrieve(subscription.getCustomer)

  (
    Option(customer.getEmail),
    Option(subscription.getItems.getData.get(0).getPlan.getProduct)
  ) match
    case (Some(email), Some(planId)) =>
      cancelPlanByMail(email, getPlanById(planId))
    case _ => IO(Left(s"Missing data: ${customer}"))

def handleEvent(
    eventRaw: String,
    sigHeader: String
) = Sync[IO]
  .blocking {
    val event = Webhook.constructEvent(
      eventRaw,
      sigHeader,
      STRIPE_WEBHOOK_KEY
    )

    val stripeObject = event.getDataObjectDeserializer.getObject.get

    val eventType = event.getType

    println(s"Event type: ${eventType}")
    eventType match

      case "customer.subscription.deleted" => handleDeleted(stripeObject)
      // Cancel, renew, update
      case "customer.subscription.updated" =>
        handleSubscriptionUpdate(stripeObject)
      // Checkout completed: update or create
      case "checkout.session.completed" =>
        handleCheckoutCompleted(stripeObject)
      case _ => IO(Left("Unhandled event type: " + event.getType))

  }
  .flatten
  .attemptTap(IO.println)
  .flatMapRight(_ => IO(Right("OK"))) |> attempt
