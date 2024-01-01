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

val WHATSAPP_PHONE_ID = sys.env.getOrElse("WHATSAPP_PHONE_ID", "")
val WHATSAPP_ACCESS_TOKEN =
  sys.env.getOrElse("WHATSAPP_ACCESS_TOKEN", "")
val WHATSAPP_VERIFY_TOKEN =
  sys.env.getOrElse("WHATSAPP_VERIFY_TOKEN", "")
val whatsappEndpoint =
  s"https://graph.facebook.com/v18.0/$WHATSAPP_PHONE_ID/messages?access_token=$WHATSAPP_ACCESS_TOKEN"

object Whatsapp:
  val logger = LoggerFactory.getLogger(getClass.getName)
  val Http = HttpClient(whatsappEndpoint)

  case class SendMessageRequest(
      to: String,
      text: Text,
      messaging_product: String = "whatsapp",
      recipient_type: String = "individual",
      `type`: String = "text"
  )

  case class SendMessageResponse(messaging_product: String)

  case class Metadata(display_phone_number: String, phone_number_id: String)

  case class Text(body: String)

  case class MessageData(
      from: String,
      id: String,
      timestamp: String,
      text: Text,
      `type`: String
  )

  case class Conversation(id: String)

  case class Pricing(
      billable: Boolean,
      pricing_model: String,
      category: String
  )

  case class Statuse(
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
    case Status(
        messaging_product: String,
        metadata: Metadata,
        statuses: List[Statuse]
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
        statuses <- c.downField("statuses").as[List[Statuse]]
      yield Value.Status(messaging_product, metadata, statuses)
    else
      Left(DecodingFailure("Missing 'messages' or 'statuses' field", c.history))

  case class Change(value: Value, field: String)

  case class Entry(id: String, changes: List[Change])

  case class WebhookPayload(`object`: String, entry: List[Entry])

  def sendMessage(
      number: String,
      message: String
  ): IO[Either[String, String]] =
    val body = SendMessageRequest(number, Text(message)).asJson.noSpaces
    Http
      .post("")
      .headers(Map("Content-Type" -> "application/json"))
      .body(body)
      .sendReq()
      .map: response =>
        logger.info(s"Message sent to $number via Whatsapp")
        response.body.flatMap(
          decode[SendMessageResponse](_).left.map(_.getMessage())
        )
      .mapRight(_ => message) |> attempt

  def handleChallenge(
      verify_token: String,
      challenge: String
  ): IO[Either[String, String]] =
    IO.pure:
      if (verify_token == WHATSAPP_VERIFY_TOKEN) Right(challenge)
      else Left("Invalid VERIFY TOKEN")

  def parseWebhook(
      payload: WebhookPayload
  ): IO[Either[String, MessageInterfacePayload]] =
    IO.pure:
      logger.info(s"Received webhook from WhatsApp: $payload")
      payload.entry.headOption
        .toRight("No 'entry'")
        .flatMap(_.changes.headOption.toRight("No 'changes'"))
        .flatMap(_.value match
          case Value.Message(_, metadata, messages) =>
            messages.headOption
              .toRight("No 'messages'")
              .map(m =>
                MessageInterfacePayload
                  .MessageReceived(
                    id = m.id,
                    phoneNumber = m.from,
                    content = m.text.body
                  )
              )
          case Value.Status(_, _, _) =>
            MessageInterfacePayload.StatusChanged.asRight
        )
