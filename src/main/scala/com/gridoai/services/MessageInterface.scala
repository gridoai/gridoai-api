package com.gridoai.services.messageInterface

import com.gridoai.utils._
import com.gridoai.utils.LRUCache
import com.gridoai.adapters.whatsapp.Whatsapp
import com.gridoai.adapters.notifications.MockedNotificationService
import com.gridoai.adapters.notifications.NotificationService
import com.gridoai.adapters.clerk.ClerkClient
import com.gridoai.services.doc.ask
import com.gridoai.services.doc.extractAndCleanText
import com.gridoai.services.doc.createOrUpdateFiles
import cats.effect.IO
import cats.implicits._
import com.gridoai.auth.AuthData
import com.gridoai.domain._
import com.gridoai.models.DocDB
import org.slf4j.LoggerFactory
import com.gridoai.parsers.FileFormat
import java.util.UUID

val logger = LoggerFactory.getLogger(getClass.getName)

def handleWebhook(
    payload: Whatsapp.WebhookPayload
)(implicit
    db: DocDB[IO],
    ns: NotificationService[IO],
    lruCache: LRUCache[String, List[WhatsAppMessage]]
): IO[Either[String, Unit]] =
  Whatsapp
    .parseWebhook(payload)
    .pure[IO]
    .flatMapRight:
      case MessageInterfacePayload.StatusChanged => IO.pure(Right("Ignored"))
      case MessageInterfacePayload.FileUpload(
            phoneNumber,
            mediaId,
            filename,
            mimeType
          ) =>
        logger.info("File received via WhatsApp...")
        getAuthData(phoneNumber)
          .flatMapRight: auth =>
            Whatsapp
              .sendMessage(
                phoneNumber,
                "Recebi seu arquivo, vou tentar processá-lo... ⌛"
              )
              .flatMapRight(_ => Whatsapp.retrieveMediaUrl(mediaId))
              .traceRight(url => s"download url: $url")
              .flatMapRight(Whatsapp.downloadMedia)
              .flatMapRight: body =>
                extractAndCleanText(
                  filename,
                  body,
                  Some(FileFormat.fromString(mimeType))
                )
              .mapLeft(_.toString)
              .mapRight: content =>
                List(
                  Document(
                    uid = UUID.randomUUID(),
                    name = filename,
                    source = Source.WhatsApp(mediaId),
                    content = content
                  )
                )
              .flatMapRight(createOrUpdateFiles(auth))
              .flatMapRight(_ =>
                Whatsapp.sendMessage(phoneNumber, "Consegui! 🥳")
              )
              .flatMapLeft: e =>
                logger.info(e)
                Whatsapp.sendMessage(
                  phoneNumber,
                  "Ops, deu errado. 😔\nTente entrar em contato com o suporte."
                )
          .flatMapLeft(_ => ().asRight.pure[IO])

      case MessageInterfacePayload.MessageReceived(id, phoneNumber, message) =>
        logger.info("Message received via WhatsApp...")
        getAuthData(phoneNumber)
          .flatMapRight: auth =>
            updateMessageCache(phoneNumber, message, id) match
              case None => ().asRight.pure[IO]
              case Some(messages) =>
                ask(auth)(
                  AskPayload(messages.map(_.toMessage), true, None, false)
                ).flatMapRight: response =>
                  checkOutOfSyncResult(phoneNumber, messages, response) match
                    case None => ().asRight.pure[IO]
                    case Some(res) =>
                      Whatsapp.sendMessage(phoneNumber, res |> formatMessage)
          .flatMapLeft(_ => ().asRight.pure[IO])

def formatMessage(askResponse: AskResponse): String =
  if (askResponse.sources.isEmpty) askResponse.message
  else
    s"${askResponse.message}\n\n📖: ${askResponse.sources.mkString(", ")}"

def updateMessageCache(phoneNumber: String, message: String, id: String)(
    implicit lruCache: LRUCache[String, List[WhatsAppMessage]]
): Option[List[WhatsAppMessage]] = synchronized:
  val oldMessages = lruCache.get(phoneNumber).getOrElse(List.empty)
  if (oldMessages.flatMap(_.ids) contains id)
    logger.info(
      "The message was already received, so it will be ignored."
    )
    None
  else
    logger.info("The message is new.")
    val messages = oldMessages match
      case restOfOldMessages :+ WhatsAppMessage(
            MessageFrom.User,
            lastMessage,
            ids
          ) =>
        logger.info("Appending new message to last user message...")
        restOfOldMessages :+ WhatsAppMessage(
          MessageFrom.User,
          s"$lastMessage\n\n$message",
          ids :+ id
        )
      case _ =>
        logger.info("Processing new message...")

        oldMessages :+ WhatsAppMessage(
          MessageFrom.User,
          message,
          List(id)
        )
    lruCache.put(phoneNumber, messages)
    Some(messages)

def checkOutOfSyncResult(
    phoneNumber: String,
    messages: List[WhatsAppMessage],
    response: AskResponse
)(implicit
    lruCache: LRUCache[String, List[WhatsAppMessage]]
): Option[AskResponse] = synchronized:
  val initialIds = messages.flatMap(_.ids)
  val newMessages = lruCache.get(phoneNumber).getOrElse(List.empty)
  val newIds = newMessages.flatMap(_.ids)
  if (newIds == initialIds)
    logger.info(
      "No new message identified. Registering and sending the answer."
    )
    lruCache.put(
      phoneNumber,
      messages :+ WhatsAppMessage(MessageFrom.Bot, response.message, List.empty)
    )
    Some(response)
  else
    logger.info("New message identified. Ignoring results...")
    None

def getAuthData(phoneNumber: String): IO[Either[String, AuthData]] =
  ClerkClient.user
    .byPhone(s"%2B$phoneNumber")
    .mapRight: user =>
      AuthData(
        orgId = user.id,
        role = "admin",
        userId = user.id,
        plan = Plan.Individual,
        customerId = None
      )
    .flatMapLeft: err =>
      logger.info(s"Failed to find the user: $err")
      Whatsapp
        .sendMessage(
          phoneNumber,
          "Não encontrei seu usuário 😔\nRegistre-se em https://gridoai.com"
        )
        .map(_ => Left(err))
