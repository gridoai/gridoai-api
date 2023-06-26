package com.gridoai.adapters.embeddingApi

import com.gridoai.domain.Embedding
import cats.effect.IO

object Mocked extends EmbeddingAPI[IO]:
  private val mockResponse = List.range(1, 768).map(_.toFloat)

  def embed(
      text: String
  ): IO[Either[String, Embedding]] =
    IO.pure(Right(mockResponse))
