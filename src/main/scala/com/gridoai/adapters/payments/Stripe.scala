package com.gridoai.adapters.stripe

import com.stripe.net.Webhook

import com.stripe._, param.CustomerCreateParams

import java.util.HashMap
import cats.effect.kernel.Sync
import cats.implicits.*

import com.stripe.model.Customer
import com.gridoai.utils.attempt

import com.gridoai.adapters.clerk.*
import com.gridoai.adapters.clerk.ClerkClient as clerk

import com.stripe.model.Subscription
import com.stripe.model.StripeObject
import com.gridoai.domain.Plan
import cats.effect.IO
import com.gridoai.utils.*
import collection.JavaConverters.collectionAsScalaIterableConverter

import com.stripe.param.billingportal.SessionCreateParams
import com.stripe.model.billingportal.Session

val STRIPE_SECRET_KEYS = getEnv("STRIPE_SECRET_KEY")
val STRIPE_WEBHOOK_KEY = getEnv("STRIPE_WEBHOOK_KEY")
val STRIPE_STARTER_PLAN_ID = getEnv("STRIPE_STARTER_PLAN_ID")
val STRIPE_PRO_PLAN_ID = getEnv("STRIPE_PRO_PLAN_ID")
val STRIPE_INDIVIDUAL_PLAN_ID = getEnv("STRIPE_INDIVIDUAL_PLAN_ID")
val STRIPE_ENTERPRISE_PLAN_ID = getEnv("STRIPE_ENTERPRISE_PLAN_ID")

val client =
  Stripe.apiKey = STRIPE_SECRET_KEYS
  com.stripe.StripeClient(STRIPE_SECRET_KEYS)

inline def getPlanById: String => Plan = {
  case p if p == STRIPE_STARTER_PLAN_ID    => Plan.Starter
  case p if p == STRIPE_PRO_PLAN_ID        => Plan.Pro
  case p if p == STRIPE_INDIVIDUAL_PLAN_ID => Plan.Individual
  case p if p == STRIPE_ENTERPRISE_PLAN_ID => Plan.Enterprise
  case _                                   => Plan.Free
}

def createCustomerPortalSession(customerId: String, baseUrl: String) =
  Sync[IO].blocking {
    val params = SessionCreateParams
      .builder()
      .setCustomer(customerId)
      .setReturnUrl(baseUrl)
      .build()

    val session = Session.create(params)
    session.getUrl
  }.attempt |> attempt

def deleteCustomerUnsafe[F[_]](oldCustomerId: String)(using Sync[F]) =
  Sync[F].blocking {
    client.customers().delete(oldCustomerId)
  }.attempt |> attempt

def deleteCustomer[F[_]](
    oldCustomerId: String,
    maybeNewCustomerID: Option[String]
)(using Sync[F]): F[Either[String, String]] =
  println(s"Deleting customer ${oldCustomerId} with ${maybeNewCustomerID}")
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
  println(s"Got customer id: ${customerId}")
  println(s"Got org name field: ${orgNameField}")
  println(s"Customer details: ${session.getCustomerDetails}")
  val necessaryData = (
    Option(session.getClientReferenceId()),
    Option(session.getCustomerDetails.getEmail),
    orgNameField.map(_.getText.getValue),
  )

  necessaryData match
    case (None, Some(email), Some(orgName)) =>
      println("Got no client reference id, fetching by email...")
      planPromise.flatMapRight: plan =>
        clerk.user
          .byEmail(email)
          .mapRight(_.id)
          .flatMapRight(
            clerk.org.upsert(
              orgName,
              plan,
              customerId,
              preUpdate = deleteCustomerOfMembership[IO](customerId)
            )
          )

    case (Some(id), Some(email), Some(orgName)) =>
      planPromise
        .flatMapRight: plan =>
          (clerk.org.upsert(
            orgName,
            plan,
            customerId,
            preUpdate = deleteCustomerOfMembership[IO](customerId)
          )(id))

    case (Some(clientId), Some(email), None) =>
      clerk.user.mergeAndUpdateMetadata(
        PublicMetadata(
          plan = Some(Plan.Individual),
          customerId = Some(customerId)
        )
      )(clientId)
    case _ => IO(Left(s"Missing data: ${necessaryData}"))

def cancelOrgPlan(orgId: String) =
  clerk.org.mergeAndUpdateMetadata(orgId, plan = (Some(Plan.Free)))

def cancelOrgSubscriptionByEmail(email: String, customerId: String) =
  clerk.user
    .getOrgByCustomerId(email, customerId)
    .mapRight(_.id)
    .flatMapRight(cancelOrgPlan)

def cancelUserPlan =
  clerk.user.mergeAndUpdateMetadata(
    PublicMetadata(
      plan = Some(Plan.Free)
    )
  )

def cancelUserPlanByEmail(email: String) =
  clerk.user.byEmail(email).mapRight(_.id).flatMapRight(cancelUserPlan)

def cancelPlanByMail(
    email: String,
    customerId: String,
    plan: Plan
) =
  println(s"Cancelling plan ${plan} for ${email}")
  plan match
    case Plan.Individual => cancelUserPlanByEmail(email)
    case Plan.Free       => IO(Left("Cannot cancel free plan"))
    case _               => cancelOrgSubscriptionByEmail(email, customerId)

def getValueFromOptionOrIO[T, L](
    option: Option[T],
    io: IO[Either[L, T]],
    noneValue: L
): IO[Either[L, T]] =
  option match
    case Some(value) => IO.pure(Right(value))
    case None        => io.mapLeft(_ => noneValue)

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
    Option(subscription.getItems.getData.get(0).getPlan.getProduct)
      .map(getPlanById),
  )
  println(
    s"Got necessary data: ${necessaryData} in event handleSubscriptionUpdate"
  )

  necessaryData match
    case (Some(_), Some(email), Some(plan)) =>
      cancelPlanByMail(email, customerId, (plan))
    case _ => IO(Left(s"Missing data: ${necessaryData}"))

def handleDeleted(
    eventObj: StripeObject
) =
  val subscription = eventObj.asInstanceOf[Subscription]
  val customer = Customer.retrieve(subscription.getCustomer)

  (
    Option(customer.getEmail),
    Option(subscription.getItems.getData.get(0).getPlan.getProduct)
      .map(getPlanById)
  ) match
    case (Some(email), Some(planId)) =>
      cancelPlanByMail(email, customer.getId, (planId))
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
      // Checkout completed: update, renew or create
      case "checkout.session.completed" =>
        handleCheckoutCompleted(stripeObject)
      case _ => IO(Left("Unhandled event type: " + event.getType))

  }
  .flatten
  .attemptTap(IO.println)
  .flatMapRight(_ => IO(Right("OK"))) |> attempt
