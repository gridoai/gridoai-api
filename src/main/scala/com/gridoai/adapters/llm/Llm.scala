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
import com.gridoai.adapters.syncCatsBackend

trait LLM[F[_]]:
  def calculateChunkTokenQuantity(chunk: Chunk): Int
  def maxTokensForChunks(
      messages: List[Message],
      basedOnDocsOnly: Boolean
  ): Int
  def chooseAction(
      messages: List[Message],
      queries: List[String],
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
  def buildQueriesToSearchDocuments(
      messages: List[Message],
      lastQueries: List[String],
      lastChunks: List[Chunk]
  ): F[Either[String, List[String]]]

def getLLMByName(llm: LLMModel): LLM[IO] =
  llm match
    case LLMModel.Gpt35Turbo => ChatGPTClient(syncCatsBackend)
    case LLMModel.Palm2      => Paml2Client
    case LLMModel.Mocked     => MockLLM[IO]

def getLLM(llm: LLMModel): LLM[IO] =
  sys.env.get("USE_MOCKED_LLM") match
    case Some("true") => MockLLM[IO]
    case _            => getLLMByName(llm)
