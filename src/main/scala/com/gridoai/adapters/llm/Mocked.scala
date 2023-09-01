package com.gridoai.adapters.llm.mocked

import cats.*
import cats.implicits.*
import com.gridoai.domain.*
import com.gridoai.adapters.llm.*

class MockLLM[F[_]: Applicative] extends LLM[F]:

  def calculateChunkTokenQuantity(chunk: Chunk): Int = 100
  def maxTokensForChunks(
      messages: List[Message],
      basedOnDocsOnly: Boolean
  ): Int = 1000

  def chooseAction(
      messages: List[Message],
      query: Option[String],
      chunks: List[Chunk],
      options: List[Action]
  ): F[Either[String, Action]] =
    options.head.asRight.pure[F]

  def answer(
      chunks: List[Chunk],
      basedOnDocsOnly: Boolean,
      messages: List[Message],
      searchedBefore: Boolean
  ): F[Either[String, String]] =
    Applicative[F].pure(Right("The response message."))

  def ask(
      chunks: List[Chunk],
      basedOnDocsOnly: Boolean,
      messages: List[Message],
      searchedBefore: Boolean
  ): F[Either[String, String]] =
    Applicative[F].pure(Right("The response message."))

  def buildQueryToSearchDocuments(
      messages: List[Message],
      lastQuery: Option[String],
      lastChunks: List[Chunk]
  ): F[Either[String, String]] =
    Applicative[F].pure(Right("The response message."))
