package com.gridoai.adapters.llm

import cats.effect.IO
import cats.effect.Async
import cats.data.EitherT
import fs2.Stream

import com.gridoai.domain._
import com.gridoai.adapters.openAiClientBackend
import com.gridoai.adapters.syncCatsBackend
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
      queries: List[String],
      chunks: List[Chunk],
      options: List[Action]
  ): EitherT[F, String, Action]
  def ask(
      chunks: List[Chunk],
      basedOnDocsOnly: Boolean,
      messages: List[Message],
      searchedBefore: Boolean
  ): Stream[F, Either[String, String]]
  def answer(
      chunks: List[Chunk],
      basedOnDocsOnly: Boolean,
      messages: List[Message],
      searchedBefore: Boolean
  ): Stream[F, Either[String, String]]
  def buildQueriesToSearchDocuments(
      messages: List[Message],
      lastQueries: List[String],
      lastChunks: List[Chunk]
  ): EitherT[F, String, List[String]]

def getLLMByName(llm: LLMModel): LLM[IO] =
  llm match
    case LLMModel.Gpt35Turbo => ChatGPTClient()
    case LLMModel.Palm2      => Paml2Client
    case LLMModel.Mocked     => MockLLM[IO]

def getLLM(llm: LLMModel): LLM[IO] =
  sys.env.get("USE_MOCKED_LLM") match
    case Some("true") => MockLLM[IO]
    case _            => getLLMByName(llm)
