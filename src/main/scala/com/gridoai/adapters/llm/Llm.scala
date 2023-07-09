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
  (sys.env.get("USE_MOCKED_EMBEDDINGS_API"), name) match
    case ((Some("1"), _) | (_, "mocked")) => MockLLM[IO]
    case (_, "palm2")                     => Paml2Client
