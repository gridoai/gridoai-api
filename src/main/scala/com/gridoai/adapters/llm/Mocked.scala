package com.gridoai.adapters.llm.mocked

import cats._
import cats.implicits._
import cats.data.EitherT
import fs2.Stream

import com.gridoai.domain._
import com.gridoai.adapters.llm._
import com.gridoai.utils._

class MockLLM[F[_]: Applicative] extends LLM[F]:

  def calculateChunkTokenQuantity(chunk: Chunk): Int = 100
  def maxTokensForChunks(
      messages: List[Message],
      basedOnDocsOnly: Boolean
  ): Int = 1000

  def chooseAction(
      messages: List[Message],
      queries: List[String],
      chunks: List[Chunk],
      options: List[Action]
  ): EitherT[F, String, Action] =
    EitherT.rightT(options.head)

  def answer(
      chunks: List[Chunk],
      basedOnDocsOnly: Boolean,
      messages: List[Message],
      searchedBefore: Boolean
  ): Stream[F, Either[String, String]] =
    Stream(Right("The response message."))

  def ask(
      chunks: List[Chunk],
      basedOnDocsOnly: Boolean,
      messages: List[Message],
      searchedBefore: Boolean
  ): Stream[F, Either[String, String]] =
    Stream(Right("The response message."))

  def buildQueriesToSearchDocuments(
      messages: List[Message],
      lastQueries: List[String],
      lastChunks: List[Chunk]
  ): EitherT[F, String, List[String]] =
    EitherT.rightT(List("The response message."))
