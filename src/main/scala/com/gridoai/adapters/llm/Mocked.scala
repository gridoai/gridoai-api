package com.gridoai.adapters.llm.mocked

import cats.*
import com.gridoai.domain.*
import com.gridoai.adapters.llm.*

class MockLLM[F[_]: Applicative] extends LLM[F]:

  def calculateChunkTokenQuantity(chunk: Chunk): Int = 100
  def askMaxTokens(
      messages: List[Message],
      basedOnDocsOnly: Boolean = true
  ): Int = 1000
  def ask(chunks: List[Chunk], basedOnDocsOnly: Boolean = true)(
      messages: List[Message]
  ): F[Either[String, String]] =
    Applicative[F].pure(Right("The response message."))

  def mergeMessages(messages: List[Message]): F[Either[String, String]] =
    Applicative[F].pure(Right("The response message."))
