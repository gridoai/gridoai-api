package com.gridoai.services.messageInterface

import com.gridoai.utils._
import com.gridoai.utils.LRUCache
import com.gridoai.adapters.whatsapp.Whatsapp
import com.gridoai.adapters.notifications.MockedNotificationService
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
    ns: NotificationService[IO],
    lruCache: LRUCache[String, List[Message]]
): IO[Either[String, Unit]] =
  Whatsapp
    .parseWebhook(payload)
    .flatMapRight:
      case MessageInterfacePayload.StatusChanged => IO.pure(Right("Ignored"))
      case MessageInterfacePayload.MessageReceived(id, phoneNumber, message) =>
        val oldMessages = lruCache.get(phoneNumber).getOrElse(List.empty)
        oldMessages match
          case _ :+ Message(MessageFrom.User, m) if (m == message) =>
            IO.pure(Right(()))
          case _ =>
            val messages = (oldMessages :+ Message(MessageFrom.User, message))
            lruCache.put(phoneNumber, messages)
            handleChat(phoneNumber, messages).mapRight: newMessage =>
              lruCache.put(
                phoneNumber,
                messages :+ Message(MessageFrom.Bot, newMessage)
              )

def handleChat(
    phoneNumber: String,
    messages: List[Message]
)(implicit
    db: DocDB[IO],
    ns: NotificationService[IO]
): IO[Either[String, String]] =
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
          messages,
          true,
          None,
          false
        )
      ).flatMapRight: res =>
        Whatsapp
          .sendMessage(
            phoneNumber,
            s"${res.message}\n\n📖: ${res.sources.mkString(", ")}"
          )
          .mapRight(_ => res.message)
    .flatMapLeft: err =>
      if (err == "No user found")
        Whatsapp.sendMessage(phoneNumber, "User not found")
      else IO.pure(Left(err))
