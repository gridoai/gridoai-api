package com.gridoai.adapters.embeddingApi

import com.gridoai.domain.*
import cats.effect.IO

object Mocked extends EmbeddingAPI[IO]:
  private val mockResponse = Embedding(
    vector = List.range(0, 767).map(_.toFloat),
    model = EmbeddingModel.Mocked
  )

  def embedChats(
      texts: List[String]
  ) =
    IO.pure(Right(texts.map(_ => mockResponse)))
  def embedChunks(chunks: List[Chunk]) =
    IO.pure(Right(chunks.map(_ => mockResponse)))
