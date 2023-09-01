package com.gridoai.adapters.llm

import cats.effect.IO
import com.gridoai.domain.Message
import com.gridoai.domain.Chunk
import com.gridoai.domain.LLMModel
import com.gridoai.domain.Action
import com.gridoai.adapters.openAiClientBackend
import com.gridoai.adapters.llm.chatGPT.ChatGPTClient
import com.gridoai.adapters.llm.palm2.Paml2Client
import com.gridoai.adapters.llm.mocked.MockLLM

trait LLM[F[_]]:
  def calculateChunkTokenQuantity(chunk: Chunk): Int
  def maxTokensForChunks(
      messages: List[Message],
      basedOnDocsOnly: Boolean
  ): Int
  def chooseAction(
      messages: List[Message],
      query: Option[String],
      chunks: List[Chunk],
      options: List[Action]
  ): F[Either[String, Action]]
  def ask(
      chunks: List[Chunk],
      basedOnDocsOnly: Boolean,
      messages: List[Message],
      searchedBefore: Boolean
  ): F[Either[String, String]]
  def answer(
      chunks: List[Chunk],
      basedOnDocsOnly: Boolean,
      messages: List[Message],
      searchedBefore: Boolean
  ): F[Either[String, String]]
  def buildQueryToSearchDocuments(
      messages: List[Message],
      lastQuery: Option[String],
      lastChunks: List[Chunk]
  ): F[Either[String, String]]

def getLLMByName(llm: LLMModel): LLM[IO] =
  llm match
    case LLMModel.Gpt35Turbo => ChatGPTClient(openAiClientBackend)
    case LLMModel.Palm2      => Paml2Client
    case LLMModel.Mocked     => MockLLM[IO]

def getLLM(llm: LLMModel): LLM[IO] =
  sys.env.get("USE_MOCKED_LLM") match
    case Some("true") => MockLLM[IO]
    case _            => getLLMByName(llm)
