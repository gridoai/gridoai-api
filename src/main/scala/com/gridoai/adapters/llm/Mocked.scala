package com.gridoai.adapters.llm

import cats._
import com.gridoai.domain._

class MockLLM[F[_]: Applicative] extends LLM[F]:

  def calculateChunkTokenQuantity(chunk: Chunk): Int = 100
  def askMaxTokens(messages: List[Message]): Int = 1000
  def ask(chunks: List[Chunk])(
      messages: List[Message]
  ): F[Either[String, String]] =
    Applicative[F].pure(Right("The response message."))

  def mergeMessages(messages: List[Message]): F[Either[String, String]] =
    Applicative[F].pure(Right("The response message."))
