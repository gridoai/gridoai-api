package com.gridoai.adapters.embeddingApi

import cats.effect.IO
import com.gridoai.domain.Embedding
import com.gridoai.domain.Chunk
import com.gridoai.adapters.HttpClient
import com.gridoai.utils.fallbackEitherM
import cats.implicits.*
import cats.Monad
import com.gridoai.utils.|>

trait EmbeddingAPI[F[_]]:
  def embedChats(texts: List[String]): F[Either[String, List[Embedding]]]
  def embedChunks(chunks: List[Chunk]): F[Either[String, List[Embedding]]]

extension [F[_]: Monad](e: EmbeddingAPI[F])
  def withFallback(fallback: EmbeddingAPI[F]): EmbeddingAPI[F] =
    new EmbeddingAPI[F]:

      def embedChats(texts: List[String]) =
        fallbackEitherM(e.embedChats, fallback.embedChats)(texts)

      def embedChunks(chunks: List[Chunk]) =
        fallbackEitherM(e.embedChunks, fallback.embedChunks)(chunks)

def getEmbeddingAPI(name: String): EmbeddingAPI[IO] =
  sys.env.get("EMBEDDING_API").getOrElse(name) |> getEmbeddingApiByName

def getEmbeddingApiByName(name: String) =
  name match
    case "mocked"     => Mocked
    case "gridoai-ml" => GridoAIML
    case "embaas" =>
      EmbaasClient(
        HttpClient("https://api.embaas.io"),
        sys.env("EMBAAS_API_KEY")
      ).withFallback(GridoAIML)
