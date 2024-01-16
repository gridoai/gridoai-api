package com.gridoai.models

import cats.effect._
import cats.implicits._
import cats.data.EitherT
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.Log.Stdout._
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.codecs.splits.SplitEpi
import dev.profunktor.redis4cats.effects.{Score, ScoreWithValue, ZRange}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.circe.generic.auto._
import io.circe.parser._
import io.circe._
import io.circe.syntax._

import com.gridoai.domain.Message
import com.gridoai.domain.WhatsAppState
import com.gridoai.domain.strToWhatsAppState
import com.gridoai.utils._

val REDIS_HOST =
  sys.env.getOrElse("REDIS_HOST", "localhost")
val REDIS_PORT =
  sys.env.getOrElse("REDIS_PORT", "6379")
val REDIS_PASSWORD = sys.env.getOrElse("REDIS_PASSWORD", "")

object RedisClient {

  def getRedis[F[_]: Async] =
    Redis[F].utf8(s"redis://default:$REDIS_PASSWORD@$REDIS_HOST:$REDIS_PORT")

  def messageKey(
      orgId: String,
      userId: String,
      conversationId: String
  ): String = s"messages:$orgId:$userId:$conversationId"

  def whatsappIdKey(phoneNumber: String): String =
    s"whatsAppIds:$phoneNumber"

  def whatsappStateKey(phoneNumber: String) = s"whatsAppState:$phoneNumber"

  def apply[F[_]: Async](redis: RedisCommands[F, String, String]) =
    implicit val logger: Logger[F] = Slf4jLogger.getLogger[F]
    new MessageDB[F] {
      def getMessages(
          orgId: String,
          userId: String,
          conversationId: String
      ): EitherT[F, String, List[Message]] =
        val key = messageKey(orgId, userId, conversationId)
        redis
          .zRange(key, 0, -1)
          .map(
            _.traverse(
              decode[Message](_).left.map(_.getMessage())
            )
          )
          .asEitherT
          .attempt

      def appendMessage(orgId: String, userId: String, conversationId: String)(
          message: Message
      ): EitherT[F, String, Message] =
        val key = messageKey(orgId, userId, conversationId)
        redis
          .zAdd(
            key,
            args = None,
            ScoreWithValue(Score(message.timestamp), message.asJson.noSpaces)
          )
          .map(_ => Right(message))
          .asEitherT
          .attempt

      def updateLastMessage(
          orgId: String,
          userId: String,
          conversationId: String
      )(
          message: Message
      ): EitherT[F, String, Message] =
        val key = messageKey(orgId, userId, conversationId)
        redis
          .zPopMax(key, 1)
          .map(_.headOption.toRight("No last element to update"))
          .asEitherT
          .attempt
          .flatMap(_ => appendMessage(orgId, userId, conversationId)(message))

      def getWhatsAppMessageIds(
          phoneNumber: String
      ): EitherT[F, String, List[String]] =
        val key = whatsappIdKey(phoneNumber)
        redis
          .zRange(key, 0, -1)
          .map(_.asRight)
          .asEitherT
          .attempt

      def appendWhatsAppMessageId(
          phoneNumber: String,
          timestamp: Long,
          id: String
      ): EitherT[F, String, String] =
        val key = whatsappIdKey(phoneNumber)
        redis
          .zAdd(
            key,
            args = None,
            ScoreWithValue(Score(timestamp), id)
          )
          .map(_ => Right(id))
          .asEitherT
          .attempt

      def getWhatsAppState(
          phoneNumber: String
      ): EitherT[F, String, WhatsAppState] =
        val key = whatsappStateKey(phoneNumber)
        redis
          .get(key)
          .map:
            case None    => WhatsAppState.NotAuthenticated.asRight
            case Some(v) => strToWhatsAppState(v)
          .asEitherT
          .attempt

      def setWhatsAppState(
          phoneNumber: String,
          state: WhatsAppState
      ): EitherT[F, String, Unit] =
        val key = whatsappStateKey(phoneNumber)
        redis
          .set(key, state.toString)
          .map(_.asRight)
          .asEitherT
          .attempt
    }
}
