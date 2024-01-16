package com.gridoai.services.payments
import com.gridoai.adapters.stripe.createCustomerPortalSession
import com.gridoai.auth.AuthData
import cats.data.EitherT

def createBillingSession(authData: AuthData)(maybeHost: Option[String]) =
  (authData.customerId, maybeHost) match
    case (Some(customerId), Some(host)) =>
      createCustomerPortalSession(customerId, host)
    case _ => EitherT.leftT("No customer id or host provided")
