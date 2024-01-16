package com.gridoai.adapters.emailApi

import com.resend._
import com.resend.services.emails.model.SendEmailRequest
import cats.implicits._
import cats.effect.kernel._
import cats.Monad
import cats.data.EitherT

import com.gridoai.adapters.HttpClient
import com.gridoai.utils._

val resend = Resend(sys.env("RESEND_API_KEY"))

class ResendClient[F[_]: Sync]() extends EmailAPI[F]:
  def sendEmail(
      to: String,
      subject: String,
      content: String
  ): EitherT[F, String, Unit] =
    (Sync[F]
      .blocking:
        try
          val sendEmailRequest = SendEmailRequest
            .builder()
            .from("no-reply@gridoai.com")
            .to(to)
            .subject(subject)
            .html(content)
            .build()

          resend.emails().send(sendEmailRequest)
          ().asRight
        catch
          case e: java.lang.Exception =>
            s"Resend failed: $e".asLeft
      )
      .asEitherT
      .attempt
