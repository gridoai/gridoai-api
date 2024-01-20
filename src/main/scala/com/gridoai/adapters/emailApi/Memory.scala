package com.gridoai.adapters.emailApi

import com.gridoai.adapters.emailApi.EmailAPI
import cats.effect.kernel.Async
import cats.data.EitherT
object InMemoryEmailer:
  def apply[F[_]: Async](): EmailAPI[F] =
    new EmailAPI[F]:
      def sendEmail(
          to: String,
          subject: String,
          content: String
      ): EitherT[F, String, Unit] =
        println(s"Sending email to $to")
        EitherT.rightT[F, String](())
