package com.gridoai.domain
import io.circe._
import io.circe.generic.auto._

import java.util.UUID

type UID = UUID

case class Document(
    uid: UID,
    name: String,
    content: String,
    url: String,
    numberOfWords: Int
)

case class Mentions(
    relationshipId: Long,
    from: UID,
    to: UID
)

case class DocCreationPayload(
    name: String,
    content: String,
    url: Option[String] = None
)

enum MessageFrom:
  case Bot, User

case class Message(from: MessageFrom, message: String)
