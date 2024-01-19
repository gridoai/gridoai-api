package com.gridoai.adapters.stripe

import com.stripe.net.Webhook

import com.stripe._, param.CustomerCreateParams

import java.util.HashMap
import cats.effect.kernel.Sync
import cats.implicits._
import cats.effect.IO
import cats.data.EitherT
import cats.Monad

import com.stripe.model.Customer

import com.stripe.model.Subscription
import com.stripe.model.StripeObject
import collection.JavaConverters.collectionAsScalaIterableConverter

import com.stripe.param.billingportal.SessionCreateParams
import com.stripe.model.billingportal.Session
import org.slf4j.LoggerFactory

import com.gridoai.utils.attempt
import com.gridoai.utils._
import com.gridoai.adapters.clerk._
import com.gridoai.adapters.clerk.ClerkClient as clerk
import com.gridoai.domain.Plan

val STRIPE_SECRET_KEY = getEnv("STRIPE_SECRET_KEY")
val STRIPE_WEBHOOK_KEY = getEnv("STRIPE_WEBHOOK_KEY")
val STRIPE_STARTER_PLAN_ID = getEnv("STRIPE_STARTER_PLAN_ID")
val STRIPE_PRO_PLAN_ID = getEnv("STRIPE_PRO_PLAN_ID")
val STRIPE_INDIVIDUAL_PLAN_ID = getEnv("STRIPE_INDIVIDUAL_PLAN_ID")
val STRIPE_ENTERPRISE_PLAN_ID = getEnv("STRIPE_ENTERPRISE_PLAN_ID")

val client =
  Stripe.apiKey = STRIPE_SECRET_KEY
  com.stripe.StripeClient(STRIPE_SECRET_KEY)

inline def getPlanById: String => Plan = {
  case p if p == STRIPE_STARTER_PLAN_ID    => Plan.Starter
  case p if p == STRIPE_PRO_PLAN_ID        => Plan.Pro
  case p if p == STRIPE_INDIVIDUAL_PLAN_ID => Plan.Individual
  case p if p == STRIPE_ENTERPRISE_PLAN_ID => Plan.Enterprise
  case _                                   => Plan.Free
}

val logger = LoggerFactory.getLogger("Stripe")

def createCustomerPortalSession(
    customerId: String,
    baseUrl: String
): EitherT[IO, String, String] =
  (Sync[IO]
    .blocking:
      try
        val params = SessionCreateParams
          .builder()
          .setCustomer(customerId)
          .setReturnUrl(baseUrl)
          .build()

        val session = Session.create(params)
        session.getUrl.asRight
      catch
        case e: java.lang.Exception =>
          s"Session creation failed: $e".asLeft
    )
    .asEitherT
    .attempt

def deleteCustomerUnsafe[F[_]: Sync](
    oldCustomerId: String
): EitherT[F, String, Customer] =
  (Sync[F]
    .blocking:
      try client.customers().delete(oldCustomerId).asRight
      catch
        case e: java.lang.Exception =>
          s"Customer deletion failed: $e".asLeft
    )
    .asEitherT
    .attempt

def deleteCustomer[F[_]: Sync](
    oldCustomerId: String,
    maybeNewCustomerID: Option[String]
): EitherT[F, String, String] =
  logger.info(s"Deleting customer ${oldCustomerId} with ${maybeNewCustomerID}")
  maybeNewCustomerID match
    case None =>
      deleteCustomerUnsafe[F](oldCustomerId).map(_ => oldCustomerId)
    case Some(value) => deleteCustomer[F](oldCustomerId, value)

def deleteCustomer[F[_]: Sync](
    oldCustomerId: String,
    newCustomerID: String
): EitherT[F, String, String] =
  if oldCustomerId != newCustomerID then
    deleteCustomerUnsafe[F](oldCustomerId).map(_ => newCustomerID)
  else EitherT.rightT[F, String](newCustomerID)

def deleteCustomerOfMembership[F[_]: Sync](newCustomerID: String)(
    membership: OrganizationMemberShipListData
): EitherT[F, String, Unit] =
  deleteCustomer[F](
    membership.organization.public_metadata.customerId,
    newCustomerID
  ).map(_ => ())

def fetchPlanOfSubscription[F[_]: Sync](
    subscriptionId: String
): EitherT[F, String, Plan] =
  (Sync[F]
    .blocking:
      try Subscription.retrieve(subscriptionId).asRight
      catch
        case e: java.lang.Exception =>
          s"Subscription fetch failed: $e".asLeft
    )
    .asEitherT
    .attempt
    .flatMapEither(getSubscriptionPlan(_).toRight("No plan found"))

def handleCheckoutCompleted(
    eventObj: StripeObject
): EitherT[IO, String, Unit] =
  val session = eventObj.asInstanceOf[com.stripe.model.checkout.Session]
  val planPromise = fetchPlanOfSubscription[IO](session.getSubscription)

  val orgNameField = session.getCustomFields.asScala.find: field =>
    val key = field.getKey
    key == "nomedaorganizao" || key == "organizationname"
  val customerId = session.getCustomer
  logger.info(s"Got customer id: ${customerId}")
  logger.info(s"Got org name field: ${orgNameField}")
  logger.info(s"Customer details: ${session.getCustomerDetails}")
  val necessaryData = (
    Option(session.getClientReferenceId()),
    Option(session.getCustomerDetails.getEmail),
    orgNameField.map(_.getText.getValue)
  )

  (necessaryData match
    case (None, Some(email), Some(orgName)) =>
      logger.info("Got no client reference id, fetching by email...")
      planPromise.flatMap: plan =>
        clerk.user
          .byEmail(email)
          .map(_.id)
          .flatMap(
            clerk.org.upsert(
              orgName,
              plan,
              customerId,
              preUpdate = deleteCustomerOfMembership[IO](customerId)
            )
          )

    case (Some(id), Some(email), Some(orgName)) =>
      planPromise
        .flatMap: plan =>
          clerk.org.upsert(
            orgName,
            plan,
            customerId,
            preUpdate = deleteCustomerOfMembership[IO](customerId)
          )(id)

    case (Some(clientId), Some(email), None) =>
      clerk.user
        .mergeAndUpdateMetadata(
          PublicMetadata(
            plan = Some(Plan.Individual),
            customerId = Some(customerId)
          )
        )(clientId)
    case _ => EitherT.leftT[IO, Unit](s"Missing data: ${necessaryData}")
  ).map(_ => ())

def cancelOrgPlan(orgId: String): EitherT[IO, String, Unit] =
  clerk.org.mergeAndUpdateMetadata(orgId, plan = (Some(Plan.Free))).map(_ => ())

def cancelOrgSubscriptionByEmail(
    email: String,
    customerId: String
): EitherT[IO, String, Unit] =
  clerk.user
    .getOrgByCustomerId(email, customerId)
    .map(_.id)
    .flatMap(cancelOrgPlan)

def cancelUserPlan(userId: String): EitherT[IO, String, Unit] =
  clerk.user
    .mergeAndUpdateMetadata(
      PublicMetadata(plan = Some(Plan.Free))
    )(userId)
    .map(_ => ())

def cancelUserPlanByEmail(email: String): EitherT[IO, String, Unit] =
  clerk.user.byEmail(email).map(_.id).flatMap(cancelUserPlan)

def cancelPlanByMail(
    email: String,
    customerId: String,
    plan: Plan
): EitherT[IO, String, Unit] =
  logger.info(s"Cancelling plan ${plan} for ${email}")
  plan match
    case Plan.Individual => cancelUserPlanByEmail(email)
    case Plan.Free       => EitherT.leftT[IO, Unit]("Cannot cancel free plan")
    case _               => cancelOrgSubscriptionByEmail(email, customerId)

def getSubscriptionPlan(sub: Subscription): Option[Plan] =
  Option(sub.getItems.getData.get(0).getPlan.getProduct)
    .map(getPlanById)

def handleSubscriptionUpdate(
    eventObj: StripeObject
): EitherT[IO, String, Unit] =
  val subscription = eventObj.asInstanceOf[Subscription]
  val customerId = subscription.getCustomer
  val customer = Customer.retrieve(customerId)

  val necessaryData = (
    Option(subscription.getCancelAt),
    Option(customer.getEmail),
    Option(subscription.getItems.getData.get(0).getPlan.getProduct)
      .map(getPlanById)
  )
  logger.info(
    s"Got necessary data: ${necessaryData} in event handleSubscriptionUpdate"
  )

  necessaryData match
    case (Some(_), Some(email), Some(plan)) =>
      cancelPlanByMail(email, customerId, (plan))
    case _ => EitherT.leftT[IO, Unit](s"Missing data: ${necessaryData}")

def handleDeleted(
    eventObj: StripeObject
): EitherT[IO, String, Unit] =
  val subscription = eventObj.asInstanceOf[Subscription]
  val customer = Customer.retrieve(subscription.getCustomer)

  (
    Option(customer.getEmail),
    Option(subscription.getItems.getData.get(0).getPlan.getProduct)
      .map(getPlanById)
  ) match
    case (Some(email), Some(planId)) =>
      cancelPlanByMail(email, customer.getId, (planId))
    case _ => EitherT.leftT[IO, Unit](s"Missing data: ${customer}")

def handleEvent(
    eventRaw: String,
    sigHeader: String
): EitherT[IO, String, String] =
  (Sync[IO]
    .blocking:
      val event = Webhook.constructEvent(
        eventRaw,
        sigHeader,
        STRIPE_WEBHOOK_KEY
      )

      val stripeObject = event.getDataObjectDeserializer.getObject.get

      val eventType = event.getType

      logger.info(s"Event type: ${eventType}")
      eventType match
        case "customer.subscription.deleted" => handleDeleted(stripeObject)
        // Cancel, renew, update
        case "customer.subscription.updated" =>
          handleSubscriptionUpdate(stripeObject)
        // Checkout completed: update, renew or create
        case "checkout.session.completed" =>
          handleCheckoutCompleted(stripeObject)
        case _ =>
          EitherT(IO.pure(Left("Unhandled event type: " + event.getType)))
            .map(_ => ())
    )
    .flatMap(_.value).asEitherT.map(_ => "OK")
