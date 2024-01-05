package com.gridoai.models

import com.gridoai.domain.Message
import com.gridoai.utils._
import com.gridoai.domain.AskResponse

import cats.implicits._
import cats.Monad
import org.slf4j.LoggerFactory
import com.gridoai.domain.MessageFrom

trait MessageDB[F[_]: Monad]:

  def getMessages(
      orgId: String,
      userId: String,
      conversationId: String
  ): F[Either[String, List[Message]]]

  def setMessages(orgId: String, userId: String, conversationId: String)(
      messages: List[Message]
  ): F[Either[String, List[Message]]]

  def getWhatsAppMessageIds(
      orgId: String,
      userId: String
  ): F[Either[String, List[String]]]

  def setWhatsAppMessageIds(
      orgId: String,
      userId: String,
      ids: List[String]
  ): F[Either[String, List[String]]]

val logger = LoggerFactory.getLogger(getClass.getName)

def updateMessageCache[F[_]: Monad](
    orgId: String,
    userId: String,
    message: String,
    id: String
)(implicit
    messageDb: MessageDB[F]
): F[Either[String, (List[Message], List[String])]] = synchronized:
  messageDb
    .getWhatsAppMessageIds(orgId, userId)
    .flatMapRight: receivedIds =>
      if (receivedIds contains id)
        logger.info(
          "The message was already received, so it will be ignored."
        )
        Left("Repeated id").pure[F]
      else
        val newIds = receivedIds :+ id
        logger.info("The message is new.")
        messageDb
          .setWhatsAppMessageIds(orgId, userId, newIds)
          .flatMapRight(_ => messageDb.getMessages(orgId, userId, "whatsapp"))
          .mapRight:
            case oldMessages :+ Message(MessageFrom.User, lastMessage) =>
              logger.info("Appending new message to last user message...")
              oldMessages :+ Message(
                MessageFrom.User,
                s"$lastMessage\n\n$message"
              )
            case oldMessages =>
              logger.info("Processing new message...")
              oldMessages :+ Message(MessageFrom.User, message)
          .flatMapRight(messageDb.setMessages(orgId, userId, "whatsapp"))
          .mapRight(_ -> newIds)

def checkOutOfSyncResult[F[_]: Monad](
    orgId: String,
    userId: String,
    initialIds: List[String]
)(
    response: AskResponse
)(implicit
    messageDb: MessageDB[F]
): F[Either[String, AskResponse]] = synchronized:
  messageDb
    .getWhatsAppMessageIds(orgId, userId)
    .flatMapRight: newIds =>
      if (newIds == initialIds)
        logger.info(
          "No new message identified. Registering and sending the answer."
        )
        messageDb
          .getMessages(orgId, userId, "whatsapp")
          .mapRight(_ :+ Message(MessageFrom.Bot, response.message))
          .flatMapRight(messageDb.setMessages(orgId, userId, "whatsapp"))
          .mapRight(_ => response)
      else
        logger.info("New message identified. Ignoring results...")
        Left("New message identified").pure[F]
