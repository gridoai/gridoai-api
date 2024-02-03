package com.gridoai.services.messageInterface

import cats.effect.IO
import cats.implicits._
import cats.data.EitherT
import java.util.UUID
import scala.util.Random
import org.slf4j.LoggerFactory
import fs2.Stream

import com.gridoai.utils._
import com.gridoai.adapters.whatsapp.Whatsapp
import com.gridoai.adapters.notifications.MockedNotificationService
import com.gridoai.adapters.notifications.NotificationService
import com.gridoai.adapters.emailApi.EmailAPI
import com.gridoai.adapters.clerk.ClerkClient
import com.gridoai.services.doc.buildAnswer
import com.gridoai.services.doc.extractAndCleanText
import com.gridoai.services.doc.createOrUpdateFiles
import com.gridoai.auth.AuthData
import com.gridoai.domain._
import com.gridoai.models.DocDB
import com.gridoai.models.MessageDB
import com.gridoai.models.checkOutOfSyncResult
import com.gridoai.models.ignoreMessageByCache
import com.gridoai.models.storeMessage
import com.gridoai.parsers.FileFormat
import com.gridoai.parsers.ExtractTextError

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
): EitherT[IO, String, Unit] =
  Whatsapp
    .parseWebhook(payload)
    .pure[IO]
    .asEitherT
    .flatMap:
      case MessageInterfacePayload.StatusChanged => EitherT.rightT(())
      case MessageInterfacePayload.FileUpload(
            id,
            from,
            to,
            mediaId,
            filename,
            mimeType,
            timestamp
          ) =>
        logger.info("File received via WhatsApp...")
        ignoreMessageByCache[IO](from, timestamp, id).flatMap:
          case None => EitherT.rightT(())
          case Some(_) =>
            authFlow(from, to).flatMap:
              case None => EitherT.rightT(())
              case Some(auth) =>
                EitherT:
                  handleUpload(
                    auth,
                    from,
                    to,
                    mediaId,
                    filename,
                    mimeType
                  ).value.start >> ().asRight.pure[IO]

      case MessageInterfacePayload.MessageReceived(
            id,
            from,
            to,
            message,
            timestamp
          ) =>
        logger.info("Message received via WhatsApp...")
        ignoreMessageByCache[IO](from, timestamp, id).flatMap:
          case None => EitherT.rightT(())
          case Some(ids) =>
            authFlow(from, to, message).flatMap:
              case None => EitherT.rightT(())
              case Some(auth) =>
                handleMessage(auth, from, to, ids)(
                  Message(MessageFrom.User, message, id, timestamp)
                )

def authFlow(from: String, to: String, message: String = "")(implicit
    messageDb: MessageDB[IO],
    emailApi: EmailAPI[IO]
): EitherT[IO, String, Option[AuthData]] =
  getAuthData(from)
    .map(_.some)
    .leftFlatMap: e =>
      logger.info("User not found.")
      messageDb
        .getWhatsAppState(from)
        .flatMap:
          case WhatsAppState.NotAuthenticated =>
            logger.info("User not authenticated yet.")
            for
              _ <- Whatsapp.sendMessage(to, from, askEmailMessage)
              x <- messageDb.setWhatsAppState(from, WhatsAppState.WaitingEmail)
            yield x
          case WhatsAppState.WaitingEmail =>
            val email = message.strip
            if (!isEmailValid(email))
              logger.info(s"Invalid email: $email")
              Whatsapp.sendMessage(to, from, askEmailMessage)
            else
              logger.info(
                s"User provided a valid email ($email), verifying if already exists..."
              )
              (for
                _ <- ClerkClient.user.byEmail(email)
                x <- Whatsapp.sendMessage(
                  to,
                  from,
                  s"Você já tem uma conta. Acesse gridoai.com e adicione sincronize seu número de celular! 😊"
                )
              yield x).leftFlatMap: e =>
                if (e != "No user found") EitherT.leftT(e)
                else sendVerificationCode(email, from, to)
          case WhatsAppState.WaitingVerificationCode(email, code, expiration) =>
            if (expiration < System.currentTimeMillis)
              for
                _ <- Whatsapp.sendMessage(
                  to,
                  from,
                  "Seu código expirou. Vou te enviar outro."
                )
                x <- sendVerificationCode(email, from, to)
              yield x
            else if (code != message.strip)
              Whatsapp.sendMessage(
                to,
                from,
                "Código incorreto. Tente novamente."
              )
            else
              val password = Random.alphanumeric.take(10).mkString
              (for
                _ <- Whatsapp.sendMessage(
                  to,
                  from,
                  "Obrigado, estou criando sua conta... ⌛"
                )
                _ <- ClerkClient.user.create(from, email, password)
                _ <- Whatsapp.sendMessage(
                  to,
                  from,
                  s"Conta criada com a senha $password! Me faça uma pergunta para testar. 😊"
                )
                x <- messageDb.setWhatsAppState(
                  from,
                  WhatsAppState.Authenticated
                )
              yield x).leftFlatMap: e =>
                Whatsapp.sendMessage(
                  to,
                  from,
                  s"Tive um problema e não consegui criar sua conta. 😔\n Erro: $e"
                )
          case WhatsAppState.Authenticated =>
            logger.info(s"Failed to find the user: $e")
            Whatsapp.sendMessage(to, from, deletedUserMessage)
        .map(_ => None)

def sendVerificationCode(
    email: String,
    from: String,
    to: String
)(implicit
    emailApi: EmailAPI[IO],
    messageDb: MessageDB[IO]
): EitherT[IO, String, Unit] =
  val code = (1 to 6).map(_ => Random.nextInt(10)).mkString
  val expiration = System.currentTimeMillis + 1000 * 60 * 10
  val emailMessage = s"$code é seu código de verificação"
  (for
    _ <- emailApi.sendEmail(email, emailMessage, emailMessage)
    _ <- Whatsapp.sendMessage(
      to,
      from,
      "Enviei um código de verificação para seu email. Digite ele aqui."
    )
    x <- messageDb.setWhatsAppState(
      from,
      WhatsAppState
        .WaitingVerificationCode(email, code, expiration)
    )
  yield x).leftFlatMap: e =>
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
): EitherT[IO, String, Unit] =
  (for
    _ <- Whatsapp.sendMessage(
      to,
      from,
      "Recebi seu arquivo, vou tentar processá-lo... ⌛"
    )
    url <- Whatsapp.retrieveMediaUrl(mediaId)
    _ = logger.info(s"download url: $url")
    body <- Whatsapp.downloadMedia(url)
    content <- extractAndCleanText(
      filename,
      body,
      Some(FileFormat.fromString(mimeType))
    ).leftMap(_.toString)
    doc = Document(
      uid = UUID.randomUUID(),
      name = filename,
      source = Source.WhatsApp(mediaId),
      content = content
    )
    _ <- createOrUpdateFiles(auth)(List(doc))
    x <- Whatsapp
      .sendMessage(to, from, "Consegui! 🥳")
      .leftWiden[String | ExtractTextError]
  yield x).leftFlatMap: e =>
    logger.error(e.toString)
    Whatsapp.sendMessage(
      to,
      from,
      e match
        case ExtractTextError(FileFormat.Plaintext, "Empty text") =>
          "Ops, deu errado. 😔\nNão foi possível extrair os textos do seu arquivo."
        case _ =>
          "Ops, deu errado. 😔\nTente entrar em contato com o suporte."
    )

def handleMessage(auth: AuthData, from: String, to: String, ids: List[String])(
    message: Message
)(implicit
    docDb: DocDB[IO],
    messageDb: MessageDB[IO],
    ns: NotificationService[IO]
): EitherT[IO, String, Unit] =
  (for
    messages <- storeMessage[IO](auth.orgId, auth.userId, message)
    sources <- buildAnswerAndRespond(auth, from, to, ids, messages)
    x <- Whatsapp.sendMessage(
      to,
      from,
      s"📖: ${sources.flatten.distinct.mkString(", ")}"
    )
  yield x).leftFlatMap: e =>
    logger.error(e)
    Whatsapp.sendMessage(
      to,
      from,
      "Ops, deu errado. 😔\nTente entrar em contato com o suporte."
    )

def buildAnswerAndRespond(
    auth: AuthData,
    from: String,
    to: String,
    ids: List[String],
    messages: List[Message]
)(implicit
    docDb: DocDB[IO],
    messageDb: MessageDB[IO],
    ns: NotificationService[IO]
) =
  (buildAnswer(auth)(
    messages,
    true,
    None,
    false
  ) |> groupStreamByParagraph)
    .subevalMap: resFragment =>
      for
        validatedResponse <- checkOutOfSyncResult[IO](
          auth.orgId,
          auth.userId,
          from,
          ids
        )(resFragment)
        _ <- Whatsapp.sendMessage(
          to,
          from,
          validatedResponse.message
        )
      yield validatedResponse.sources
    .compileOutput
    .leftMap(_.mkString(","))

def groupStreamByParagraph(
    s: Stream[IO, Either[String, AskResponse]]
): Stream[IO, Either[String, AskResponse]] =
  s.subflatMap: m =>
    Stream.emits:
      splitMessageByLineBreak(m.message).map(r =>
        AskResponse(r, m.sources).asRight
      )
  .groupAdjacentBy:
      case Right(r) => r.message == "\n"
      case Left(e)  => true
    .filter(!_._1)
    .map(_._2.toList |> partitionEithers)
    .leftMap(_.mkString)
    .subMap: r =>
      AskResponse(
        message = r.map(_.message).mkString,
        sources = r.flatMap(_.sources).distinct
      )

def splitMessageByLineBreak(s: String): List[String] =
  (s.startsWith("\n"), s.endsWith("\n")) match
    case (true, true) =>
      ("\n" +: splitLineBreak(
        s.stripSuffix("\n").stripPrefix("\n")
      )) :+ "\n"
    case (false, true) =>
      splitLineBreak(s.stripSuffix("\n")) :+ "\n"
    case (true, false) =>
      "\n" +: splitLineBreak(s.stripPrefix("\n"))
    case (false, false) =>
      if (s.contains("\n")) splitLineBreak(s)
      else List(s)

def splitLineBreak(s: String): List[String] =
  s.split("\n")
    .filter(_ != "")
    .flatMap(Array(_, "\n"))
    .dropRight(1)
    .toList

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
): EitherT[IO, String, AuthData] =
  ClerkClient.user
    .byPhones(phoneNumber |> calcPhoneVariants)
    .map: user =>
      AuthData(
        orgId = user.id,
        role = "admin",
        userId = user.id,
        plan = Plan.Individual,
        customerId = None
      )
