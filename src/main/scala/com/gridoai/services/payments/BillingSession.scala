package com.gridoai.services.payments

import cats.effect.IO
import cats.data.EitherT

import com.gridoai.adapters.stripe.createCustomerPortalSession
import com.gridoai.auth.AuthData

def createBillingSession(authData: AuthData)(
    maybeHost: Option[String]
): EitherT[IO, String, String] =
  (authData.customerId, maybeHost) match
    case (Some(customerId), Some(host)) =>
      createCustomerPortalSession(customerId, host)
    case _ => EitherT.leftT[IO, String]("No customer id or host provided")
