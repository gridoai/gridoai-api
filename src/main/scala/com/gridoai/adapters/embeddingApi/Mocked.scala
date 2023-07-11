package com.gridoai.adapters.embeddingApi

import com.gridoai.domain.EmbeddingOutput
import cats.effect.IO

object Mocked extends EmbeddingAPI[IO]:
  private val mockResponse = EmbeddingOutput(vector=List.range(1, 768).map(_.toFloat), model="mocked")

  def embed(
      text: String
  ): IO[Either[String, EmbeddingOutput]] =
    IO.pure(Right(mockResponse))
