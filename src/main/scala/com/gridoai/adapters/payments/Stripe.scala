package com.gridoai.adapters.payments

import com.gridoai.adapters.UserCreated
import com.gridoai.utils.|>
import com.stripe._, param.CustomerCreateParams

import java.util.HashMap
import cats.effect.kernel.Sync
import cats.implicits.*
import com.stripe.model.Customer
import com.gridoai.utils.attempt

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

def handlePayment(event: String) =
  // turns into strpe pay
  ???
