package com.gridoai.adapters.embeddingApi

import com.gridoai.domain.Embedding
import cats.effect.IO

object Mocked extends EmbeddingAPI[IO]:
  private val mockResponse = Embedding(
    vector = List.range(1, 768).map(_.toFloat),
    model = "mocked"
  )

  def embed(
      text: String
  ): IO[Either[String, Embedding]] =
    IO.pure(Right(mockResponse))
