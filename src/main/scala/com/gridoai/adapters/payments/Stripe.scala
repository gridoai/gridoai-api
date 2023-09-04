package com.gridoai.adapters.payments

import com.gridoai.adapters.UserCreated
import com.gridoai.utils.|>
import com.stripe._, param.CustomerCreateParams

import java.util.HashMap
import cats.effect.kernel.Sync
import cats.implicits.*
import com.stripe.model.Customer
import com.gridoai.utils.attempt
import com.stripe.net.ApiResource
import com.stripe.model.Event

import com.stripe.net.Webhook
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
import com.stripe.param.billingportal.ConfigurationCreateParams
import com.stripe.model.billingportal.Configuration
import com.stripe.param.billingportal.SessionCreateParams
import com.stripe.model.billingportal.Session

val stripeKey = sys.env
  .get("STRIPE_SECRET_KEY")
  .getOrElse(throw new Exception("STRIPE_SECRET_KEY not found"))

val client =
  Stripe.apiKey = stripeKey
  com.stripe.StripeClient(stripeKey)

inline val starterPlanId = "prod_OXYliuLZeUgGCo"
inline val proPlanId = "prod_OXYlBswtpMHlF4"
inline val individualPlanId = "prod_OYJo6qEO5Y0Cu9"

inline def getPlanById: String => Plan = {
  case `starterPlanId`    => Plan.Starter
  case `proPlanId`        => Plan.Pro
  case `individualPlanId` => Plan.Individual
  case _                  => Plan.Free
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

def handleCheckoutCompleted(
    eventObj: StripeObject
) =
  val session = eventObj.asInstanceOf[com.stripe.model.checkout.Session]

  val orgNameField = session.getCustomFields.asScala.find: field =>
    val key = field.getKey
    key == "nomedaorganizao" || key == "organizationname"
  val customerId = session.getCustomer
  val necessaryData = (
    Option(session.getClientReferenceId()),
    Option(session.getCustomerDetails.getEmail),
    orgNameField.map(_.getText.getValue),
  )
  def addCustomerID =
    user.mergeAndUpdateMetadata(
      PublicMetadata(
        customerId = Some(customerId)
      )
    )
  val plan = Plan.Starter // Temporary, next webhook will update it
  necessaryData match
    case (None, Some(email), Some(orgName)) =>
      println("Got no client reference id, fetching by email...")
      user
        .list(email)
        .mapRight(_.head.id)
        .flatMapRight(addCustomerID)
        .mapRight(_.id)
        .flatMapRight(org.create(orgName, plan))

    case (Some(id), Some(email), Some(orgName)) =>
      addCustomerID(id)
        .mapRight(_.id)
        .flatMapRight(org.create(orgName, plan))

    case (Some(clientId), Some(email), None) =>
      user.mergeAndUpdateMetadata(
        PublicMetadata(
          plan = Some(Plan.Individual),
          customerId = Some(customerId)
        )
      )(clientId)
    case _ => IO(Left(s"Missing data: ${necessaryData}"))

def cancelOrgSubscriptionByEmail(email: String) =
  getActiveOrgByEmail(email).flatMapRight(org.delete)

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

import cats.effect.IO

def getValueFromOptionOrIO[T, L](
    option: Option[T],
    io: IO[Either[L, T]],
    noneValue: L
): IO[Either[L, T]] = {
  option match {
    case Some(value) => IO.pure(Right(value))
    case None        => io.map(_.left.map(_ => noneValue))
  }
}
def upgradePlan(
    email: String,
    plan: Plan,
    customerId: Option[String],
    maybeClientId: Option[String]
) =
  println(s"Upgrading plan to ${plan} for ${email} with ${customerId}")
  val clientId = getValueFromOptionOrIO(
    maybeClientId,
    user.byEmail(email).mapRight(_.id),
    "User not found"
  )
  plan match
    case (Plan.Individual) =>
      clientId.flatMapRight(
        user.mergeAndUpdateMetadata(
          PublicMetadata(
            plan = Some(plan),
            customerId = customerId
          )
        )
      )

    case (Plan.Free) => IO(Left("Cannot upgrade to free plan"))
    case _           => getActiveOrgByEmail(email) !> (org.updatePlan(_, plan))

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
    // val event1 = Webhook.constructEvent(
    //   eventRaw,
    //   sigHeader,
    //   sys.env
    //     .get("WEBHOOK_KEY")
    //     .getOrElse(throw new Exception("WEBHOOK_KEY not found"))
    // )
    // println(event1.toJson())
    // Append to file
    val event = ApiResource.GSON.fromJson(eventRaw, classOf[Event])

    val stripeObject = event.getDataObjectDeserializer.getObject.get

    val eventType = event.getType
    // val file = new java.io.File(eventType + event.getCreated() + ".json")
    // val bw = new java.io.BufferedWriter(new java.io.FileWriter(file, true))
    // bw.write(eventRaw)
    // bw.close()
    println(s"Event type: ${eventType}")
    eventType match

      case "customer.subscription.deleted" => handleDeleted(stripeObject)
      case "customer.subscription.updated" | "customer.subscription.created" =>
        handleSubscriptionUpdate(stripeObject)
      case "checkout.session.completed" =>
        handleCheckoutCompleted(stripeObject)
      case _ => IO(Left("Unhandled event type: " + event.getType))
  }
  .flatten
  .attemptTap(IO.println)
  .flatMapRight(_ => IO(Right("OK")))
