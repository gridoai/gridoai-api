package com.gridoai.models

import cats.effect._
import cats.implicits._
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.Log.Stdout._
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.codecs.splits.SplitEpi
import dev.profunktor.redis4cats.effect.Log.NoOp._
import dev.profunktor.redis4cats.effects.{Score, ScoreWithValue, ZRange}
import io.circe.generic.auto._
import io.circe.parser._
import io.circe._
import io.circe.syntax._

import com.gridoai.domain.Message
import com.gridoai.utils._

val REDIS_HOST =
  sys.env.getOrElse("REDIS_HOST", "localhost")
val REDIS_PORT =
  sys.env.getOrElse("REDIS_PORT", "6379")
val REDIS_PASSWORD = sys.env.getOrElse("REDIS_PASSWORD", "")

object RedisClient {

  def getRedis[F[_]: Async] =
    Redis[F].utf8(s"redis://default:$REDIS_PASSWORD@$REDIS_HOST:$REDIS_PORT")

  def apply[F[_]: Async](redis: RedisCommands[F, String, String]) =
    new MessageDB[F] {
      def getMessages(
          orgId: String,
          userId: String,
          conversationId: String
      ): F[Either[String, List[Message]]] =
        val key = s"messages:$orgId:$userId:$conversationId"
        redis
          .zRange(key, 0, -1)
          .map(
            _.traverse(
              decode[Message](_).left.map(_.getMessage())
            )
          ) |> attempt

      def appendMessage(orgId: String, userId: String, conversationId: String)(
          message: Message
      ): F[Either[String, Message]] =
        val key = s"messages:$orgId:$userId:$conversationId"
        redis
          .zAdd(
            key,
            args = None,
            ScoreWithValue(Score(message.timestamp), message.asJson.noSpaces)
          )
          .map(_ => Right(message)) |> attempt

      def updateLastMessage(
          orgId: String,
          userId: String,
          conversationId: String
      )(
          message: Message
      ): F[Either[String, Message]] =
        val key = s"messages:$orgId:$userId:$conversationId"
        redis
          .zPopMax(key, 1)
          .map(_.headOption.toRight("No last element to update"))
          .flatMapRight(_ =>
            appendMessage(orgId, userId, conversationId)(message)
          ) |> attempt

      def getWhatsAppMessageIds(
          orgId: String,
          userId: String
      ): F[Either[String, List[String]]] =
        val key = s"whatsAppIds:$orgId:$userId"
        redis
          .zRange(key, 0, -1)
          .map(_.asRight) |> attempt

      def appendWhatsAppMessageId(
          orgId: String,
          userId: String,
          timestamp: Long,
          id: String
      ): F[Either[String, String]] =
        val key = s"whatsAppIds:$orgId:$userId"
        redis
          .zAdd(
            key,
            args = None,
            ScoreWithValue(Score(timestamp), id)
          )
          .map(_ => Right(id)) |> attempt

    }
}
