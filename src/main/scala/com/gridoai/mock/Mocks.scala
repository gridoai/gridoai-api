package com.gridoai.mock

import com.gridoai.domain.ChunkWithEmbedding
import com.gridoai.domain.Embedding
import com.gridoai.domain.EmbeddingModel
import com.gridoai.domain.Document
import com.gridoai.domain.Chunk
import com.gridoai.domain.Source

import java.util.UUID

val mockedChunk = ChunkWithEmbedding(
  chunk = Chunk(
    uid = UUID.fromString("694b8567-8c93-45c6-8051-34be4337e740"),
    documentUid = UUID.fromString("694b8567-8c93-45c6-8051-34be4337e740"),
    documentName = "Sky observations",
    documentSource = Source.Upload,
    content = "The sky is blue",
    tokenQuantity = 4,
    startPos = 0,
    endPos = 0
  ),
  embedding = Embedding(vector = List(1, 2, 3), model = EmbeddingModel.Mocked)
)

val mockedDocument = Document(
  uid = UUID.fromString("694b8567-8c93-45c6-8051-34be4337e740"),
  name = "Sky observations",
  source = Source.Upload,
  content = "The sky is blue"
)
