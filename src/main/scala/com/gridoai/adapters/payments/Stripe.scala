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
import com.gridoai.utils.flatMapRight

val stripeKey = sys.env
  .get("STRIPE_SECRET_KEY")
  .getOrElse(throw new Exception("STRIPE_SECRET_KEY not found"))
val _ = Stripe.apiKey = stripeKey
val client = com.stripe.StripeClient(stripeKey)

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
    Option(customer.getMetadata.get("clerkId")),
    Option(customer.getName).getOrElse("User"),
    Option(subscription.getItems.getData.get(0).getPlan.getId)
  )

  necessaryData match
    case (Some(email), Some(clerkId), (userName), Some(productId)) =>
      val plan = getPlanById(productId)

      ClerkClient.createOrg(clerkId, s"${userName} Org", plan)
    case _ => IO(Left(s"Missing data: ${necessaryData}"))

def handleUpdated(eventObj: StripeObject) =
  val subscription = eventObj.asInstanceOf[Subscription]
  val customer = Customer.retrieve(subscription.getCustomer)

  val necessaryData = (
    Option(customer.getEmail),
    Option(customer.getMetadata.get("clerkId")),
    Option(subscription.getItems().getData().get(0).getId())
  )

  necessaryData match
    case (Some(email), Some(clerkId), Some(productId)) =>
      val plan = getPlanById(productId)

      ClerkClient
        .listMembershipsOfUser(clerkId)
        .flatMapRight(
          _.data
            .find(_.organization.created_by === clerkId)
            .map(_.organization.id)
            .map(ClerkClient.updateOrganizationPlan(_, plan))
            .getOrElse(IO(Left("No org found")))
        )
    case _ => IO(Left(s"Missing data: ${necessaryData}"))

def handleDeleted(
    eventObj: StripeObject
) = {
  val subscription = eventObj.asInstanceOf[Subscription]
  val customer = Customer.retrieve(subscription.getCustomer)
  Option(customer.getMetadata.get("clerkId")) match
    case Some(clerkId) =>
      ClerkClient
        .listMembershipsOfUser(clerkId)
        .flatMapRight(
          _.data
            .find(_.organization.created_by === clerkId)
            .map(_.organization.id)
            .map(ClerkClient.deleteOrg)
            .getOrElse(IO(Left("No org found")))
        )
    case None =>
      IO(Left(s"Missing clerkId"))

}

def handleEvent(
    eventRaw: String,
    sigHeader: String
) = Sync[IO]
  .blocking {
    val event1 = Webhook.constructEvent(
      eventRaw,
      sigHeader,
      sys.env
        .get("WEBHOOK_KEY")
        .getOrElse(throw new Exception("WEBHOOK_KEY not found"))
    )
    println(event1.toJson())
    val event = ApiResource.GSON.fromJson(eventRaw, classOf[Event])
    val dataObjectDeserializer = event.getDataObjectDeserializer
    val stripeObject = dataObjectDeserializer.getObject.get

    val eventType = event.getType
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
