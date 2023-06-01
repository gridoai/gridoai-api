package com.programandonocosmos.domain
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

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
