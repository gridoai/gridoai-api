package com.gridoai.services.payments
import com.gridoai.adapters.stripe.createCustomerPortalSession
import com.gridoai.auth.AuthData
import cats.effect.IO

def createBillingSession(authData: AuthData)(maybeHost: Option[String]) =
  (authData.customerId, maybeHost) match
    case (Some(customerId), Some(host)) =>
      createCustomerPortalSession(customerId, host)
    case _ => IO.pure(Left("No customer id or host provided"))
