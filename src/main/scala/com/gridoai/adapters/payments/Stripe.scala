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
import com.stripe.model.Subscription
import com.stripe.model.StripeObject
import com.gridoai.domain.Plan
import cats.effect.IO
import com.gridoai.utils.*
import cats.data.EitherT

val stripeKey = sys.env
  .get("STRIPE_SECRET_KEY")
  .getOrElse(throw new Exception("STRIPE_SECRET_KEY not found"))

val client =
  Stripe.apiKey = stripeKey
  com.stripe.StripeClient(stripeKey)

def getPlanById: String => Plan =
  case "prod_OXYliuLZeUgGCo" => Plan.Starter
  case "prod_OXYlBswtpMHlF4" => Plan.Pro
  case _                     => Plan.Free

def createCostumerFromClerkPayload[F[_]](
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

def handleCreateCostumer[F[_]](
    payload: UserCreated,
    authorization: String
)(using F: Sync[F]): F[Either[String, String]] =

  println("Creating costumer...")
  println((authorization, sys.env.get("WEBHOOK_KEY")))
  sys.env.get("WEBHOOK_KEY") match
    case Some(key) if key == authorization =>
      createCostumerFromClerkPayload(payload)
        .map(_.toJson)
        .attempt |> attempt
    case _ =>
      "Unauthorized".asLeft.pure[F]

def handleCreated(eventObj: StripeObject) =
  val subscription = eventObj.asInstanceOf[Subscription]
  println(subscription.getItems.getData.get(0).getPlan.getId)
  val customer = Customer.retrieve(subscription.getCustomer)

  val necessaryData = (
    Option(customer.getEmail),
    Option(customer.getName).flatMap(_.split(" ").headOption).getOrElse("User"),
    Option(subscription.getItems.getData.get(0).getPlan.getProduct)
  )

  necessaryData match
    case (Some(email), (userName), Some(productId)) =>
      val plan = getPlanById(productId)
      ClerkClient
        .listUsers(email)
        .mapRight(_.head.id)
        .flatMapRight(ClerkClient.createOrg(s"${userName} Org", plan))
    case _ => IO(Left(s"Missing data: ${necessaryData}"))

def getUserOrgByEmail(email: String) =
  (for
    users <- EitherT(ClerkClient.listUsers(email))
    uid <- EitherT.fromOption(users.headOption, "No user found").map(_.id)
    memberships <- EitherT(ClerkClient.listMembershipsOfUser(uid))
    orgOpt = memberships.data
      .find(_.organization.created_by === uid)
    org <- EitherT.fromOption(orgOpt, "No org found")
  yield org.organization.id).value

def cancelSubscriptionByEmail(email: String) =
  getUserOrgByEmail(email).flatMapRight(ClerkClient.deleteOrg)

def handleUpdated(eventObj: StripeObject) =
  val subscription = eventObj.asInstanceOf[Subscription]
  val customer = Customer.retrieve(subscription.getCustomer)

  val necessaryData = (
    Option(subscription.getCancelAt()),
    Option(customer.getEmail),
    Option(subscription.getItems.getData.get(0).getPlan.getProduct)
  )

  necessaryData match
    case (None, Some(email), Some(productId)) =>
      val plan = getPlanById(productId)
      getUserOrgByEmail(email)
        .flatMapRight(
          ClerkClient.updateOrganizationPlan(
            _,
            plan
          )
        )
    case (Some(_), Some(email), _) =>
      cancelSubscriptionByEmail(email).mapRight(_ => ())
    case _ => IO(Left(s"Missing data: ${necessaryData}"))

def handleDeleted(
    eventObj: StripeObject
) = {
  val subscription = eventObj.asInstanceOf[Subscription]
  val customer = Customer.retrieve(subscription.getCustomer)

  Option(customer.getEmail) match
    case Some(email) => cancelSubscriptionByEmail(email)
    case _           => IO(Left(s"Missing data: ${customer}"))

}

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
    val dataObjectDeserializer = event.getDataObjectDeserializer
    val stripeObject = dataObjectDeserializer.getObject.get

    val eventType = event.getType
    val file = new java.io.File(eventType + event.getCreated() + ".json")
    val bw = new java.io.BufferedWriter(new java.io.FileWriter(file, true))
    bw.write(eventRaw)
    bw.close()
    println(s"Event type: ${eventType}")
    eventType match
      case "customer.subscription.created" => handleCreated(stripeObject)
      case "customer.subscription.deleted" => handleDeleted(stripeObject)
      case "customer.subscription.updated" => handleUpdated(stripeObject)
      case _ => IO(Left("Unhandled event type: " + event.getType))
  }
  .flatten
  .attemptTap(IO.println)
  .flatMapRight(_ => IO(Right("OK")))
