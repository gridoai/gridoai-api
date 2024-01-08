package com.gridoai.adapters.whatsapp

import com.gridoai.utils.*
import cats.effect.IO
import cats.implicits._
import com.gridoai.adapters.*
import com.gridoai.domain.*
import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.*
import io.circe.syntax.*
import sttp.model.{Header, MediaType}
import io.circe.derivation.Configuration
import io.circe.derivation.ConfiguredEnumCodec
import org.slf4j.LoggerFactory
import java.net.URL
import scala.util.Using
import java.net.HttpURLConnection
import cats.effect.kernel.Sync
import sttp.client3._

val WHATSAPP_ACCESS_TOKEN =
  sys.env.getOrElse("WHATSAPP_ACCESS_TOKEN", "")
val WHATSAPP_VERIFY_TOKEN =
  sys.env.getOrElse("WHATSAPP_VERIFY_TOKEN", "")
val WHATSAPP_ENDPOINT = "https://graph.facebook.com/v18.0/"

object Whatsapp:
  val logger = LoggerFactory.getLogger(getClass.getName)
  val Http = HttpClient(WHATSAPP_ENDPOINT)

  case class SendMessageRequest(
      to: String,
      text: Text,
      messaging_product: String = "whatsapp",
      recipient_type: String = "individual",
      `type`: String = "text"
  )

  case class SendMessageResponse(messaging_product: String)
  case class RetrieveMediaUrlResponse(url: String)

  case class Metadata(display_phone_number: String, phone_number_id: String)

  case class Text(body: String)
  case class Document(
      filename: String,
      mime_type: String,
      sha256: String,
      id: String
  )

  enum MessageData:
    case TextMessage(
        from: String,
        id: String,
        timestamp: Long,
        text: Text,
        `type`: String
    )
    case DocumentMessage(
        from: String,
        id: String,
        timestamp: String,
        document: Document,
        `type`: String
    )

  given Decoder[MessageData] = Decoder.instance: c =>
    val keys = c.keys.getOrElse(Set.empty).toList
    if (keys contains "text")
      for
        from <- c.downField("from").as[String]
        id <- c.downField("id").as[String]
        timestamp <- c.downField("timestamp").as[Long]
        text <- c.downField("text").as[Text]
        _type <- c.downField("type").as[String]
      yield MessageData.TextMessage(from, id, timestamp, text, _type)
    else if (keys contains "document")
      for
        from <- c.downField("from").as[String]
        id <- c.downField("id").as[String]
        timestamp <- c.downField("timestamp").as[String]
        document <- c.downField("document").as[Document]
        _type <- c.downField("type").as[String]
      yield MessageData.DocumentMessage(from, id, timestamp, document, _type)
    else
      Left(DecodingFailure("Missing 'text' or 'document' field", c.history))

  case class Conversation(id: String)

  case class Pricing(
      billable: Boolean,
      pricing_model: String,
      category: String
  )

  case class Status(
      id: String,
      status: String,
      timestamp: Int,
      recipient_id: String
  )

  enum Value:
    case Message(
        messaging_product: String,
        metadata: Metadata,
        messages: List[MessageData]
    )
    case StatusValue(
        messaging_product: String,
        metadata: Metadata,
        statuses: List[Status]
    )

  given Decoder[Value] = Decoder.instance: c =>
    val keys = c.keys.getOrElse(Set.empty).toList
    if (keys contains "messages")
      for
        messaging_product <- c.downField("messaging_product").as[String]
        metadata <- c.downField("metadata").as[Metadata]
        messages <- c.downField("messages").as[List[MessageData]]
      yield Value.Message(messaging_product, metadata, messages)
    else if (keys contains "statuses")
      for
        messaging_product <- c.downField("messaging_product").as[String]
        metadata <- c.downField("metadata").as[Metadata]
        statuses <- c.downField("statuses").as[List[Status]]
      yield Value.StatusValue(messaging_product, metadata, statuses)
    else
      Left(DecodingFailure("Missing 'messages' or 'statuses' field", c.history))

  case class Change(value: Value, field: String)

  case class Entry(id: String, changes: List[Change])

  case class WebhookPayload(`object`: String, entry: List[Entry])

  def sendMessage(
      from: String,
      to: String,
      message: String
  ): IO[Either[String, Unit]] =
    val body = SendMessageRequest(to, Text(message)).asJson.noSpaces
    Http
      .post(s"$from/messages?access_token=$WHATSAPP_ACCESS_TOKEN")
      .headers(Map("Content-Type" -> "application/json"))
      .body(body)
      .sendReq()
      .map: response =>
        logger.info(s"Message sent from $from to $to via Whatsapp")
        response.body.flatMap(
          decode[SendMessageResponse](_).left.map(_.getMessage())
        )
      .mapRight(_ => ()) |> attempt

  def retrieveMediaUrl(mediaId: String): IO[Either[String, String]] =
    Http
      .get(mediaId)
      .headers(
        Map(
          "Content-Type" -> "application/json",
          "Authorization" -> s"Bearer $WHATSAPP_ACCESS_TOKEN"
        )
      )
      .sendReq()
      .map: response =>
        response.body.flatMap(
          decode[RetrieveMediaUrlResponse](_).left.map(_.getMessage())
        )
      .mapRight(_.url) |> attempt

  def downloadMedia(url: String): IO[Either[String, Array[Byte]]] =
    HttpClient(url)
      .get("")
      .headers(
        Map(
          "Authorization" -> s"Bearer $WHATSAPP_ACCESS_TOKEN",
          "Accept" -> "*/*",
          "Accept-Language" -> "*",
          "Accept-Encoding" -> "gzip, deflate, br",
          "User-Agent" -> "WhatsApp/2.19.81 A"
        )
      )
      .response(asByteArray)
      .sendReq()
      .map(_.body) |> attempt

  def handleChallenge(
      verify_token: String,
      challenge: String
  ): IO[Either[String, String]] =
    IO.pure:
      if (verify_token == WHATSAPP_VERIFY_TOKEN) Right(challenge)
      else Left("Invalid VERIFY TOKEN")

  def parseWebhook(
      payload: WebhookPayload
  ): Either[String, MessageInterfacePayload] =
    logger.info(s"Received webhook from WhatsApp: $payload")
    payload.entry.headOption
      .toRight("No 'entry'")
      .flatMap(_.changes.headOption.toRight("No 'changes'"))
      .flatMap(_.value match
        case Value.Message(_, metadata, messages) =>
          messages.headOption
            .toRight("No 'messages'")
            .map:
              case MessageData.TextMessage(from, id, timestamp, text, _) =>
                MessageInterfacePayload
                  .MessageReceived(
                    id = id,
                    from = from,
                    to = metadata.phone_number_id,
                    content = text.body,
                    timestamp = timestamp
                  )
              case MessageData.DocumentMessage(from, id, _, document, _) =>
                MessageInterfacePayload
                  .FileUpload(
                    from = from,
                    to = metadata.phone_number_id,
                    mediaId = document.id,
                    filename = document.filename,
                    mimeType = document.mime_type
                  )
        case Value.StatusValue(_, _, _) =>
          MessageInterfacePayload.StatusChanged.asRight
      )
