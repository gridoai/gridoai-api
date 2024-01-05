package com.gridoai.models

import cats.effect._
import cats.implicits._
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.Log.Stdout._
import com.gridoai.domain.Message

object RedisClient {
  def apply[F[_]: Async]() = new MessageDB[F] {
    def getMessages(
        orgId: String,
        userId: String,
        conversationId: String
    ): F[Either[String, List[Message]]] = ???

    def setMessages(orgId: String, userId: String, conversationId: String)(
        messages: List[Message]
    ): F[Either[String, List[Message]]] = ???

    def getWhatsAppMessageIds(
        orgId: String,
        userId: String
    ): F[Either[String, List[String]]] = ???

    def setWhatsAppMessageIds(
        orgId: String,
        userId: String,
        ids: List[String]
    ): F[Either[String, List[String]]] = ???

  }
}
