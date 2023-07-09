package com.gridoai.adapters.llm.mocked

import cats._
import cats.implicits._
import com.gridoai.domain._

class MockLLM[F[_]: Applicative] extends LLM[F]:

  def ask(
      documents: List[Document],
      messages: List[Message]
  ): F[Either[String, String]] =
    Applicative[F].pure(Right("The response message."))
