package com.gridoai.models

import com.gridoai.domain.Message
import com.gridoai.utils._
import com.gridoai.domain.AskResponse
import com.gridoai.domain.MessageFrom
import com.gridoai.domain.WhatsAppState

import cats.implicits._
import cats.Monad
import org.slf4j.LoggerFactory
import java.util.UUID

trait MessageDB[F[_]]:

  // TODO: [GRI-200] Implement chat pagination in Redis
  def getMessages(
      orgId: String,
      userId: String,
      conversationId: String
  ): F[Either[String, List[Message]]]

  def appendMessage(orgId: String, userId: String, conversationId: String)(
      message: Message
  ): F[Either[String, Message]]

  def updateLastMessage(orgId: String, userId: String, conversationId: String)(
      message: Message
  ): F[Either[String, Message]]

  def getWhatsAppMessageIds(
      phoneNumber: String
  ): F[Either[String, List[String]]]

  def appendWhatsAppMessageId(
      phoneNumber: String,
      timestamp: Long,
      id: String
  ): F[Either[String, String]]

  def getWhatsAppState(
      phoneNumber: String
  ): F[Either[String, WhatsAppState]]

  def setWhatsAppState(
      phoneNumber: String,
      state: WhatsAppState
  ): F[Either[String, Unit]]

val logger = LoggerFactory.getLogger(getClass.getName)

def ignoreMessageByCache[F[_]: Monad](
    phoneNumber: String,
    timestamp: Long,
    id: String
)(implicit
    messageDb: MessageDB[F]
): F[Either[String, Option[List[String]]]] = synchronized:
  messageDb
    .getWhatsAppMessageIds(phoneNumber)
    .flatMapRight: receivedIds =>
      if (receivedIds contains id)
        logger.info(
          "The message was already received, so it will be ignored."
        )
        None.asRight.pure[F]
      else
        logger.info("The message is new.")
        messageDb
          .appendWhatsAppMessageId(phoneNumber, timestamp, id)
          .mapRight(_ => Some(receivedIds :+ id))

def storeMessage[F[_]: Monad](
    orgId: String,
    userId: String,
    message: Message
)(implicit
    messageDb: MessageDB[F]
): F[Either[String, List[Message]]] = synchronized:
  messageDb
    .getMessages(orgId, userId, "whatsapp")
    .flatMapRight:
      case oldMessages :+ Message(
            MessageFrom.User,
            lastMessage,
            id,
            timestamp
          ) =>
        logger.info("Concatenating new message to last user message...")
        val updatedMessage = Message(
          MessageFrom.User,
          s"$lastMessage\n\n${message.message}",
          message.id,
          message.timestamp
        )
        messageDb
          .updateLastMessage(orgId, userId, "whatsapp")(updatedMessage)
          .mapRight(_ => oldMessages :+ updatedMessage)
      case oldMessages =>
        logger.info("Appending new message...")
        messageDb
          .appendMessage(orgId, userId, "whatsapp")(message)
          .mapRight(_ => oldMessages :+ message)

def checkOutOfSyncResult[F[_]: Monad](
    orgId: String,
    userId: String,
    phoneNumber: String,
    initialIds: List[String]
)(
    response: AskResponse
)(implicit
    messageDb: MessageDB[F]
): F[Either[String, AskResponse]] = synchronized:
  messageDb
    .getWhatsAppMessageIds(phoneNumber)
    .flatMapRight: newIds =>
      if (newIds == initialIds)
        logger.info(
          "No new message identified. Registering and sending the answer."
        )
        messageDb
          .appendMessage(orgId, userId, "whatsapp")(
            Message(MessageFrom.Bot, response.message)
          )
          .mapRight(_ => response)
      else
        logger.info("New message identified. Ignoring results...")
        Left("New message identified").pure[F]
