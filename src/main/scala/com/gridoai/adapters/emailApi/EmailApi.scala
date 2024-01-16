package com.gridoai.adapters.emailApi
import cats.data.EitherT

trait EmailAPI[F[_]]:
  def sendEmail(
      to: String,
      subject: String,
      content: String
  ): EitherT[F, String, Unit]
