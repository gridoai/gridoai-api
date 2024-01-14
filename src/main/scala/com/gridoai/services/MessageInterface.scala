package com.gridoai.services.messageInterface

import com.gridoai.utils._
import com.gridoai.adapters.whatsapp.Whatsapp
import com.gridoai.adapters.notifications.MockedNotificationService
import com.gridoai.adapters.notifications.NotificationService
import com.gridoai.adapters.emailApi.EmailAPI
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
import com.gridoai.models.ignoreMessageByCache
import com.gridoai.models.storeMessage
import org.slf4j.LoggerFactory
import com.gridoai.parsers.FileFormat
import java.util.UUID
import scala.util.Random

val logger = LoggerFactory.getLogger(getClass.getName)

val deletedUserMessage = """Parece que sua conta foi deletado 😔
Para continuar utilizando, entre em contato com o suporte."""
val askEmailMessage = """Parece que você ainda não tem uma conta.
Digite seu email para que eu crie uma conta para você! 😊"""

def handleWebhook(
    payload: Whatsapp.WebhookPayload
)(implicit
    docDb: DocDB[IO],
    messageDb: MessageDB[IO],
    ns: NotificationService[IO],
    emailApi: EmailAPI[IO]
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
        authFlow(from, to).flatMapRight:
          case None => ().asRight.pure[IO]
          case Some(auth) =>
            handleUpload(auth, from, to, mediaId, filename, mimeType)

      case MessageInterfacePayload.MessageReceived(
            id,
            from,
            to,
            message,
            timestamp
          ) =>
        logger.info("Message received via WhatsApp...")
        ignoreMessageByCache[IO](from, timestamp, id).flatMapRight:
          case None => ().asRight.pure[IO]
          case Some(ids) =>
            authFlow(from, to, message).flatMapRight:
              case None => ().asRight.pure[IO]
              case Some(auth) =>
                handleMessage(auth, from, to, ids)(
                  Message(MessageFrom.User, message, id, timestamp)
                )

def authFlow(from: String, to: String, message: String = "")(implicit
    messageDb: MessageDB[IO],
    emailApi: EmailAPI[IO]
): IO[Either[String, Option[AuthData]]] =
  getAuthData(from)
    .mapRight(_.some)
    .flatMapLeft: e =>
      logger.info("User not found.")
      messageDb
        .getWhatsAppState(from)
        .flatMapRight:
          case WhatsAppState.NotAuthenticated =>
            logger.info("User not authenticated yet.")
            Whatsapp
              .sendMessage(to, from, askEmailMessage)
              .flatMapRight(_ =>
                messageDb
                  .setWhatsAppState(from, WhatsAppState.WaitingEmail)
              )
          case WhatsAppState.WaitingEmail =>
            val email = message.strip
            if (!isEmailValid(email))
              logger.info(s"Invalid email: $email")
              Whatsapp
                .sendMessage(to, from, askEmailMessage)
            else
              logger.info(
                s"User provided a valid email ($email), verifying if already exists..."
              )
              ClerkClient.user
                .byEmail(email)
                .flatMapRight(_ =>
                  Whatsapp
                    .sendMessage(
                      to,
                      from,
                      s"Você já tem uma conta. Acesse gridoai.com e adicione sincronize seu número de celular! 😊"
                    )
                )
                .flatMapLeft: e =>
                  if (e != "No user found") e.asLeft.pure[IO]
                  else sendVerificationCode(email, from, to)
          case WhatsAppState.WaitingVerificationCode(email, code, expiration) =>
            if (expiration < System.currentTimeMillis)
              Whatsapp
                .sendMessage(
                  to,
                  from,
                  "Seu código expirou. Vou te enviar outro."
                )
                .flatMapRight(_ => sendVerificationCode(email, from, to))
            else if (code != message.strip)
              Whatsapp
                .sendMessage(
                  to,
                  from,
                  "Código incorreto. Tente novamente."
                )
            else
              val password = Random.alphanumeric.take(10).mkString
              Whatsapp
                .sendMessage(
                  to,
                  from,
                  "Obrigado, estou criando sua conta... ⌛"
                )
                .flatMapRight(_ =>
                  ClerkClient.user.create(from, email, password)
                )
                .flatMapRight(_ =>
                  Whatsapp.sendMessage(
                    to,
                    from,
                    s"Conta criada com a senha $password! Me faça uma pergunta para testar. 😊"
                  )
                )
                .flatMapRight(_ =>
                  messageDb
                    .setWhatsAppState(from, WhatsAppState.Authenticated)
                )
                .flatMapLeft: e =>
                  Whatsapp.sendMessage(
                    to,
                    from,
                    s"Tive um problema e não consegui criar sua conta. 😔\n Erro: $e"
                  )
          case WhatsAppState.Authenticated =>
            logger.info(s"Failed to find the user: $e")
            Whatsapp
              .sendMessage(to, from, deletedUserMessage)
        .mapRight(_ => None)

def sendVerificationCode(
    email: String,
    from: String,
    to: String
)(implicit
    emailApi: EmailAPI[IO],
    messageDb: MessageDB[IO]
): IO[Either[String, Unit]] =
  val code = (1 to 6).map(_ => Random.nextInt(10)).mkString
  val expiration = System.currentTimeMillis + 1000 * 60 * 10
  val emailMessage = s"$code é seu código de verificação"
  emailApi
    .sendEmail(email, emailMessage, emailMessage)
    .flatMapRight(_ =>
      Whatsapp
        .sendMessage(
          to,
          from,
          "Enviei um código de verificação para seu email. Digite ele aqui."
        )
    )
    .flatMapRight(_ =>
      messageDb
        .setWhatsAppState(
          from,
          WhatsAppState
            .WaitingVerificationCode(email, code, expiration)
        )
    )
    .flatMapLeft: e =>
      Whatsapp
        .sendMessage(
          to,
          from,
          s"Tive um problema e não consegui te enviar o código de verificação por email. 😔\nContate o suporte com o seguinte erro:\n$e"
        )

def handleUpload(
    auth: AuthData,
    from: String,
    to: String,
    mediaId: String,
    filename: String,
    mimeType: String
)(implicit
    docDb: DocDB[IO],
    messageDb: MessageDB[IO]
): IO[Either[String, Unit]] =
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

def handleMessage(auth: AuthData, from: String, to: String, ids: List[String])(
    message: Message
)(implicit
    docDb: DocDB[IO],
    messageDb: MessageDB[IO],
    ns: NotificationService[IO]
): IO[Either[String, Unit]] =
  storeMessage[IO](auth.orgId, auth.userId, message)
    .flatMapRight: messages =>
      buildAnswer(auth)(messages, true, None, false)
    .flatMapRight(
      checkOutOfSyncResult[IO](
        auth.orgId,
        auth.userId,
        from,
        ids
      )
    )
    .flatMapRight: response =>
      Whatsapp.sendMessage(
        to,
        from,
        response |> formatMessage
      )

def formatMessage(askResponse: AskResponse): String =
  if (askResponse.sources.isEmpty) askResponse.message
  else
    s"${askResponse.message}\n\n📖: ${askResponse.sources.mkString(", ")}"

def isEmailValid(email: String): Boolean =
  val regex =
    """(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\[(?:(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9]))\.){3}(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9])|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\])""".r
  regex.pattern.matcher(email).matches

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

def getAuthData(
    phoneNumber: String
): IO[Either[String, AuthData]] =
  ClerkClient.user
    .byPhones(phoneNumber |> calcPhoneVariants)
    .mapRight: user =>
      AuthData(
        orgId = user.id,
        role = "admin",
        userId = user.id,
        plan = Plan.Individual,
        customerId = None
      )
