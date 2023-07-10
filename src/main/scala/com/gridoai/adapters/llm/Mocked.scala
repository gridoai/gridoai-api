package com.gridoai.adapters.llm

import cats._
import com.gridoai.domain._

class MockLLM[F[_]: Applicative] extends LLM[F]:

  def ask(documents: List[Document])(
      messages: List[Message]
  ): F[Either[String, String]] =
    Applicative[F].pure(Right("The response message."))

  def mergeMessages(messages: List[Message]): F[Either[String, String]] =
    Applicative[F].pure(Right("The response message."))
