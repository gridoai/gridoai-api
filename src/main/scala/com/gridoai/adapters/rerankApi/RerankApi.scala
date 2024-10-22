package com.gridoai.adapters.rerankApi

import cats.effect.IO
import cats.implicits._
import cats.data.EitherT
import cats.Monad

import com.gridoai.adapters.HttpClient
import com.gridoai.utils._
import com.gridoai.domain.Chunk
import com.gridoai.domain.RelevantChunk

case class RerankPayload(query: String, chunks: List[Chunk])

trait RerankAPI[F[_]]:
  def rerank(payload: RerankPayload): EitherT[F, String, List[RelevantChunk]]

extension [F[_]: Monad](e: RerankAPI[F])
  def withFallback(fallback: RerankAPI[F]): RerankAPI[F] =
    new RerankAPI[F]:

      def rerank(payload: RerankPayload) =
        fallbackEitherM(e.rerank, fallback.rerank)(payload)

def getRerankAPI(name: String): RerankAPI[IO] =
  sys.env.get("RERANK_API").getOrElse(name) |> getRerankApiByName

def getRerankApiByName(name: String) =
  name match
    case "cohere" =>
      CohereClient(
        HttpClient("https://api.cohere.ai"),
        sys.env("COHERE_API_KEY")
      )
