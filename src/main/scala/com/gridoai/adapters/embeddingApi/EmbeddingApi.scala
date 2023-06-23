package com.gridoai.adapters.embeddingApi

import cats.effect.IO
import com.gridoai.domain.Embedding

trait EmbeddingAPI[F[_]]:
  def embed(text: String): F[Either[String, Embedding]]

def getEmbeddingAPI(name: String): EmbeddingAPI[IO] =
  name match
    case "gridoai-ml" => GridoAIML
    case "mocked"     => Mocked
