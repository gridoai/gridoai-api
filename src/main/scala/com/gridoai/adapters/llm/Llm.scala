package com.gridoai.adapters.llm

import cats.effect.IO
import com.gridoai.domain.Message
import com.gridoai.domain.Chunk
import com.gridoai.adapters.catsBackendSync

trait LLM[F[_]]:
  def calculateChunkTokenQuantity(chunk: Chunk): Int
  def askMaxTokens(messages: List[Message]): Int
  def ask(chunks: List[Chunk])(
      messages: List[Message]
  ): F[Either[String, String]]

  def mergeMessages(messages: List[Message]): F[Either[String, String]]

def getLLMByName(name: String): LLM[IO] =
  name match
    case "gpt3.5turbo" => ChatGPTClient(catsBackendSync)
    case "palm2"       => Paml2Client
    case "mocked"      => MockLLM[IO]

def getLLM(name: String): LLM[IO] =
  sys.env.get("USE_MOCKED_LLM") match
    case Some("1") => MockLLM[IO]
    case _         => getLLMByName(name)
