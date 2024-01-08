package com.gridoai.services.messageInterface

import com.gridoai.utils._
import com.gridoai.adapters.whatsapp.Whatsapp
import com.gridoai.adapters.notifications.MockedNotificationService
import com.gridoai.adapters.notifications.NotificationService
import com.gridoai.adapters.clerk.ClerkClient
import com.gridoai.services.doc.buildAnswer
import com.gridoai.services.doc.extractAndCleanText
import com.gridoai.services.doc.createOrUpdateFiles
import cats.effect.IO
import cats.implicits._
import com.gridoai.auth.AuthData
import com.gridoai.domain._
import com.gridoai.models.DocDB
import com.gridoai.models.MessageDB
import com.gridoai.models.checkOutOfSyncResult
import com.gridoai.models.updateMessageCache
import org.slf4j.LoggerFactory
import com.gridoai.parsers.FileFormat
import java.util.UUID

val logger = LoggerFactory.getLogger(getClass.getName)

val deletedUserMessage = """Parece que seu usuário foi deletado 😔
|Para continuar utilizando, entre em contato com o suporte.""".stripMargin

def handleWebhook(
    payload: Whatsapp.WebhookPayload
)(implicit
    docDb: DocDB[IO],
    messageDb: MessageDB[IO],
    ns: NotificationService[IO]
): IO[Either[String, Unit]] =
  Whatsapp
    .parseWebhook(payload)
    .pure[IO]
    .flatMapRight:
      case MessageInterfacePayload.StatusChanged => IO.pure(Right("Ignored"))
      case MessageInterfacePayload.FileUpload(
            from,
            to,
            mediaId,
            filename,
            mimeType
          ) =>
        logger.info("File received via WhatsApp...")
        messageDb
          .getWhatsAppState(from)
          .flatMapRight: state =>
            handleUpload(from, to, mediaId, filename, mimeType).flatMapLeft(_ =>
              messageDb.setWhatsAppState(from, WhatsAppState.WaitingEmail)
            )

      case MessageInterfacePayload.MessageReceived(
            id,
            from,
            to,
            message,
            timestamp
          ) =>
        logger.info("Message received via WhatsApp...")
        getAuthData(to, from)
          .flatMapRight: auth =>
            updateMessageCache[IO](
              auth.orgId,
              auth.userId,
              Message(MessageFrom.User, message, id, timestamp),
              id
            )
              .flatMapRight: (messages, ids) =>
                buildAnswer(auth)(messages, true, None, false)
                  .flatMapRight(
                    checkOutOfSyncResult[IO](
                      auth.orgId,
                      auth.userId,
                      ids
                    )
                  )
              .flatMapRight: response =>
                Whatsapp.sendMessage(to, from, response |> formatMessage)
          .flatMapLeft(_ => ().asRight.pure[IO])

def handleUpload(
    from: String,
    to: String,
    mediaId: String,
    filename: String,
    mimeType: String
)(implicit
    docDb: DocDB[IO],
    messageDb: MessageDB[IO]
) =
  getAuthData(to, from)
    .flatMapRight: auth =>
      Whatsapp
        .sendMessage(
          to,
          from,
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
        .flatMapRight(_ => Whatsapp.sendMessage(to, from, "Consegui! 🥳"))
        .flatMapLeft: e =>
          logger.info(e)
          Whatsapp.sendMessage(
            to,
            from,
            "Ops, deu errado. 😔\nTente entrar em contato com o suporte."
          )
    .flatMapLeft: err =>
      logger.info(s"Failed to find the user: $err")
      Whatsapp
        .sendMessage(from, to, deletedUserMessage)
        .map(_ => Left(err))

def formatMessage(askResponse: AskResponse): String =
  if (askResponse.sources.isEmpty) askResponse.message
  else
    s"${askResponse.message}\n\n📖: ${askResponse.sources.mkString(", ")}"

def calcPhoneVariants(phoneNumber: String): List[String] =
  val brazilianNumbers = if (phoneNumber.startsWith("55"))
    val number = phoneNumber.takeRight(8)
    val ddd = phoneNumber.drop(2).take(2)
    List(s"55$ddd$number", s"55${ddd}9$number", phoneNumber)
  else List(phoneNumber)
  brazilianNumbers
    .flatMap(p => List(s"%2B$p", p))
    .distinct
    .traceFn(l => s"All phone variants: $l")

def getAuthData(from: String, to: String): IO[Either[String, AuthData]] =
  ClerkClient.user
    .byPhones(to |> calcPhoneVariants)
    .mapRight: user =>
      AuthData(
        orgId = user.id,
        role = "admin",
        userId = user.id,
        plan = Plan.Individual,
        customerId = None
      )
