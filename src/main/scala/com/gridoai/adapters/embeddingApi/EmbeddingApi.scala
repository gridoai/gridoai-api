package com.gridoai.adapters.embeddingApi

import cats.effect.IO
import com.gridoai.domain.Embedding

trait EmbeddingAPI[F[_]]:
  def embed(text: String): F[Either[String, Embedding]]

def getEmbeddingAPI(name: String): EmbeddingAPI[IO] =
  (sys.env.get("USE_MOCKED_EMBEDDINGS_API"), name) match
    case ((Some("1"), _) | (_, "mocked")) => Mocked
    case (_, "gridoai-ml")                => GridoAIML
