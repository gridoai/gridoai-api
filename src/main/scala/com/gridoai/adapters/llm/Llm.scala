package com.gridoai.adapters.llm

import cats.effect.IO
import com.gridoai.domain.Message
import com.gridoai.domain.Chunk
import com.gridoai.domain.LLMModel
import com.gridoai.adapters.catsBackendSync

trait LLM[F[_]]:
  def calculateChunkTokenQuantity(chunk: Chunk): Int
  def askMaxTokens(messages: List[Message]): Int
  def ask(chunks: List[Chunk])(
      messages: List[Message]
  ): F[Either[String, String]]

  def mergeMessages(messages: List[Message]): F[Either[String, String]]

def getLLMByName(llm: LLMModel): LLM[IO] =
  llm match
    case LLMModel.Gpt35Turbo => ChatGPTClient(catsBackendSync)
    case LLMModel.Palm2      => Paml2Client
    case LLMModel.Mocked     => MockLLM[IO]

def getLLM(llm: LLMModel): LLM[IO] =
  sys.env.get("USE_MOCKED_LLM") match
    case Some("true") => MockLLM[IO]
    case _            => getLLMByName(llm)
