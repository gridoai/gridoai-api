package com.gridoai.adapters.llm

import com.gridoai.domain.Message
import com.gridoai.domain.Document
import cats.effect.IO

trait LLM[F[_]]:
  def ask(
      documents: List[Document],
      messages: List[Message]
  ): F[Either[String, String]]

def getLLM(name: String): LLM[IO] =
  name match
    case "palm2" => Paml2Client
