package com.gridoai.adapters.embeddingApi

import cats.effect.IO
import com.gridoai.domain.Embedding
import com.gridoai.domain.Chunk
import com.gridoai.adapters.HttpClient
import com.gridoai.utils.fallbackEitherM
import cats.implicits.*
import cats.Monad

trait EmbeddingAPI[F[_]]:
  def embedChat(text: String): F[Either[String, Embedding]]
  def embedChunks(chunks: List[Chunk]): F[Either[String, List[Embedding]]]

extension [F[_]: Monad](e: EmbeddingAPI[F])
  def withFallback(fallback: EmbeddingAPI[F]): EmbeddingAPI[F] =
    new EmbeddingAPI[F]:

      def embedChat(text: String) =
        fallbackEitherM(e.embedChat, fallback.embedChat)(text)

      def embedChunks(chunks: List[Chunk]) =
        fallbackEitherM(e.embedChunks, fallback.embedChunks)(chunks)

def getEmbeddingAPI(name: String): EmbeddingAPI[IO] =
  sys.env.get("USE_MOCKED_EMBEDDINGS_API") match
    case Some("true") => Mocked
    case _            => getEmbeddingApiByName(name)

def getEmbeddingApiByName(name: String) =
  name match
    case "mocked"     => Mocked
    case "gridoai-ml" => GridoAIML
    case "embaas" =>
      EmbaasClient(
        HttpClient("https://api.embaas.io"),
        sys.env("EMBAAS_API_KEY")
      ).withFallback(GridoAIML)
