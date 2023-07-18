package com.gridoai.adapters.llm

import cats.effect.IO
import com.gridoai.domain.Message
import com.gridoai.domain.Chunk

trait LLM[F[_]]:
  val maxInputToken: Int

  def ask(chunks: List[Chunk])(
      messages: List[Message]
  ): F[Either[String, String]]
  def mergeMessages(messages: List[Message]): F[Either[String, String]]

def getLLMByName(name: String): LLM[IO] =
  name match
    case "palm2"  => Paml2Client
    case "mocked" => MockLLM[IO]

def getLLM(name: String): LLM[IO] =
  sys.env.get("USE_MOCKED_LLM") match
    case Some("1") => MockLLM[IO]
    case _         => getLLMByName(name)
