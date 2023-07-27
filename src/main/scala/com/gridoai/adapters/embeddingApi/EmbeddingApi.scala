package com.gridoai.adapters.embeddingApi

import cats.effect.IO
import com.gridoai.domain.Embedding
import com.gridoai.adapters.HttpClient
import com.gridoai.utils.fallbackEitherM
import cats.implicits.*
import cats.Monad

trait EmbeddingAPI[F[_]]:
  def embed(text: String): F[Either[String, Embedding]]
  def embedMany(text: List[String]): F[Either[String, List[Embedding]]]

extension [F[_]: Monad](e: EmbeddingAPI[F])
  def withFallback(fallback: EmbeddingAPI[F]): EmbeddingAPI[F] =
    new EmbeddingAPI[F]:

      def embed(text: String) =
        fallbackEitherM(e.embed, fallback.embed)(text)

      def embedMany(text: List[String]) =
        fallbackEitherM(e.embedMany, fallback.embedMany)(text)

def getEmbeddingAPI(name: String): EmbeddingAPI[IO] =
  sys.env.get("USE_MOCKED_EMBEDDINGS_API") match
    case Some("1") => Mocked
    case _         => getEmbeddingApiByName(name)

def getEmbeddingApiByName(name: String) =
  name match
    case "mocked"     => Mocked
    case "gridoai-ml" => GridoAIML
    case "embaas" =>
      EmbaasClient(
        HttpClient("https://api.embaas.io"),
        sys.env("EMBAAS_API_KEY")
      ).withFallback(GridoAIML)
