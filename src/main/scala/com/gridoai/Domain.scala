package com.gridoai.domain
import io.circe._
import io.circe.generic.auto._

import java.util.UUID

type UID = UUID
type Embedding = List[Float]

case class Document(
    uid: UID,
    name: String,
    source: String,
    content: String,
    tokenQuantity: Int
)

case class DocumentWithEmbedding(
    document: Document,
    embedding: Embedding
)

case class SimilarDocument(
    document: Document,
    distance: Float
)

case class DocumentCreationPayload(
    name: String,
    source: String,
    content: String
):
  def toDocument(
      uid: UID,
      tokenQuantity: Int
  ) = Document(
    uid = uid,
    name = this.name,
    source = this.source,
    content = this.source,
    tokenQuantity = tokenQuantity
  )

enum MessageFrom:
  case Bot, User

case class Message(from: MessageFrom, message: String)
