package com.gridoai.adapters.emailApi

trait EmailAPI[F[_]]:
  def sendEmail(
      to: String,
      subject: String,
      content: String
  ): F[Either[String, Unit]]
