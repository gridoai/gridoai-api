package com.gridoai.adapters.embeddingApi

import com.gridoai.domain.*
import cats.effect.IO

object Mocked extends EmbeddingAPI[IO]:
  private val mockResponse = Embedding(
    vector = List.range(0, 768).map(_.toFloat),
    model = EmbeddingModel.Mocked
  )

  def embed(
      text: String
  ): IO[Either[String, Embedding]] =
    IO.pure(Right(mockResponse))
