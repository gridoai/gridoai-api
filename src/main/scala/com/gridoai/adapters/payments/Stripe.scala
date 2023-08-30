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
import com.stripe.model.PaymentIntent
import com.stripe.model.PaymentMethod
import com.stripe.net.Webhook
import com.gridoai.adapters.ClerkClient
import com.stripe.model.Subscription

val client = com.stripe.StripeClient(
  sys.env
    .get("STRIPE_SECRET_KEY")
    .getOrElse(throw new Exception("STRIPE_SECRET_KEY not found"))
)

def createCostumerFromClerkPayload[F[_]](
    payload: UserCreated
)(using F: Sync[F]) =
  Sync[F].blocking:
    CustomerCreateParams
      .builder()
      .setEmail(payload.data.email_addresses.head.email_address)
      .setName(payload.data.first_name + " " + payload.data.last_name)
      .setMetadata(
        new HashMap[String, String]() {
          put("clerk_id", payload.data.id)
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
        .map(_.toJson())
        .attempt |> attempt
    case _ =>
      "Unauthorized".asLeft.pure[F]

def handleDeletionEvent(orgId: String) =
  ClerkClient.deleteOrg(orgId)

def handleEvent[F[_]](
    eventRaw: String,
    sigHeader: String
)(using F: Sync[F]): F[Either[String, String]] = Sync[F].blocking {
  println(s"Event from stripe: ${eventRaw}")

  val event = Webhook.constructEvent(
    eventRaw,
    sigHeader,
    sys.env
      .get("WEBHOOK_KEY")
      .getOrElse(throw new Exception("WEBHOOK_KEY not found"))
  )

  // Deserialize the nested object inside the event
  val dataObjectDeserializer = event.getDataObjectDeserializer
  val stripeObject = dataObjectDeserializer.getObject.get

  // Move this to separate case if we add other types of events
  val subscription = stripeObject.asInstanceOf[Subscription]
  val eventType = event.getType
  println(s"Event type: ${eventType}")
  // Handle the event
  eventType match

    case "customer.subscription.created" =>
      subscription.getItems().getData().get(0)
      "???"
    case "customer.subscription.deleted" =>
      "???"
    case "customer.subscription.updated" =>
      "???"
    // Then define and call a method to handle the successful attachment of a PaymentMethod.
    // handlePaymentMethodAttached(paymentMethod);

    // ... handle other event types
    case _ =>
      System.out.println("Unhandled event type: " + event.getType)
      "???"

  // val event = ApiResource.GSON.fromJson(eventRaw, classOf[Event])
  // val dataObjectDeserializer = event.getDataObjectDeserializer()
}.attempt |> attempt
