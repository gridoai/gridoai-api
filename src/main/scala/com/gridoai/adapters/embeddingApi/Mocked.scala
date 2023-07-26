package com.gridoai.adapters.embeddingApi

import com.gridoai.domain.*
import cats.effect.IO

object Mocked extends EmbeddingAPI[IO]:
  private val mockResponse = Embedding(
    vector = List.range(0, 767).map(_.toFloat),
    model = EmbeddingModel.Mocked
  )

  def embed(
      text: String
  ) =
    IO.pure(Right(mockResponse))
  def embedMany(text: List[String]) =
    IO.pure(Right(text.map(_ => mockResponse)))
