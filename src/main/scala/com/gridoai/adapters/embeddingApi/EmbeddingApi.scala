package com.gridoai.adapters.embeddingApi

import cats.effect.IO
import com.gridoai.domain.Embedding

trait EmbeddingAPI[F[_]]:
  def embed(text: String): F[Either[String, Embedding]]
  def embedMany(text: List[String]): F[Either[String, List[Embedding]]]

def getEmbeddingAPI(name: String): EmbeddingAPI[IO] =
  sys.env.get("USE_MOCKED_EMBEDDINGS_API") match
    case Some("1") => Mocked
    case _         => getEmbeddingApiByName(name)

def getEmbeddingApiByName(name: String) =
  name match
    case "mocked"     => Mocked
    case "gridoai-ml" => GridoAIML
