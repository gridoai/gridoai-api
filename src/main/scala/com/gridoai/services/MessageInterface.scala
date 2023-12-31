package com.gridoai.services.messageInterface

import com.gridoai.utils._
import com.gridoai.adapters.whatsapp.Whatsapp
import com.gridoai.adapters.notifications.NotificationService
import com.gridoai.adapters.clerk.ClerkClient
import com.gridoai.services.doc.ask
import cats.effect.IO
import com.gridoai.auth.AuthData
import com.gridoai.domain._
import com.gridoai.models.DocDB

def handleWebhook(
    payload: Whatsapp.WebhookPayload
)(implicit
    db: DocDB[IO],
    ns: NotificationService[IO]
): IO[Either[String, Unit]] =
  Whatsapp
    .parseWebhook(payload)
    .flatMapRight:
      case MessageInterfacePayload.StatusChanged => IO.pure(Right(()))
      case MessageInterfacePayload.MessageReceived(phoneNumber, message) =>
        handleMessage(phoneNumber, message)

def handleMessage(
    phoneNumber: String,
    message: String
)(implicit
    db: DocDB[IO],
    ns: NotificationService[IO]
): IO[Either[String, Unit]] =
  ClerkClient.user
    .byPhone(s"%2B$phoneNumber")
    .flatMapRight: user =>
      ask(
        AuthData(
          orgId = user.id,
          role = "admin",
          userId = user.id,
          plan = Plan.Individual,
          customerId = None
        )
      )(
        AskPayload(
          List(Message(MessageFrom.User, message)),
          true,
          None,
          false
        )
      ).flatMapRight: res =>
        Whatsapp.sendMessage(phoneNumber, res.message)
    .flatMapLeft: err =>
      if (err == "No user found")
        Whatsapp.sendMessage(phoneNumber, "User not found")
      else IO.pure(Left(err))
