package com.gridoai.models

import cats.implicits._
import cats.Monad
import cats.data.EitherT
import org.slf4j.LoggerFactory
import java.util.UUID

import com.gridoai.domain.Message
import com.gridoai.utils._
import com.gridoai.domain.AskResponse
import com.gridoai.domain.MessageFrom
import com.gridoai.domain.WhatsAppState

trait MessageDB[F[_]]:

  // TODO: [GRI-200] Implement chat pagination in Redis
  def getMessages(
      orgId: String,
      userId: String,
      conversationId: String
  ): EitherT[F, String, List[Message]]

  def appendMessage(orgId: String, userId: String, conversationId: String)(
      message: Message
  ): EitherT[F, String, Message]

  def updateLastMessage(orgId: String, userId: String, conversationId: String)(
      message: Message
  ): EitherT[F, String, Message]

  def getWhatsAppMessageIds(
      phoneNumber: String
  ): EitherT[F, String, List[String]]

  def appendWhatsAppMessageId(
      phoneNumber: String,
      timestamp: Long,
      id: String
  ): EitherT[F, String, String]

  def getWhatsAppState(
      phoneNumber: String
  ): EitherT[F, String, WhatsAppState]

  def setWhatsAppState(
      phoneNumber: String,
      state: WhatsAppState
  ): EitherT[F, String, Unit]

val logger = LoggerFactory.getLogger(getClass.getName)

def ignoreMessageByCache[F[_]: Monad](
    phoneNumber: String,
    timestamp: Long,
    id: String
)(implicit
    messageDb: MessageDB[F]
): EitherT[F, String, Option[List[String]]] = synchronized:
  messageDb
    .getWhatsAppMessageIds(phoneNumber)
    .flatMap: receivedIds =>
      if (receivedIds contains id)
        logger.info(
          "The message was already received, so it will be ignored."
        )
        EitherT.rightT(None)
      else
        logger.info("The message is new.")
        messageDb
          .appendWhatsAppMessageId(phoneNumber, timestamp, id)
          .map(_ => Some(receivedIds :+ id))

def storeMessage[F[_]: Monad](
    orgId: String,
    userId: String,
    message: Message
)(implicit
    messageDb: MessageDB[F]
): EitherT[F, String, List[Message]] = synchronized:
  messageDb
    .getMessages(orgId, userId, "whatsapp")
    .flatMap:
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
          .map(_ => oldMessages :+ updatedMessage)
      case oldMessages =>
        logger.info("Appending new message...")
        messageDb
          .appendMessage(orgId, userId, "whatsapp")(message)
          .map(_ => oldMessages :+ message)

def checkOutOfSyncResult[F[_]: Monad](
    orgId: String,
    userId: String,
    phoneNumber: String,
    initialIds: List[String]
)(
    response: AskResponse
)(implicit
    messageDb: MessageDB[F]
): EitherT[F, String, AskResponse] = synchronized:
  messageDb
    .getWhatsAppMessageIds(phoneNumber)
    .flatMap: newIds =>
      if (newIds == initialIds)
        logger.info(
          "No new message identified. Registering and sending the answer."
        )
        messageDb
          .appendMessage(orgId, userId, "whatsapp")(
            Message(MessageFrom.Bot, response.message)
          )
          .map(_ => response)
      else
        logger.info("New message identified. Ignoring results...")
        EitherT.leftT("New message identified")
