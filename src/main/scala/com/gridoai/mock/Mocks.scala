package com.gridoai.mock

import com.gridoai.domain.ChunkWithEmbedding
import com.gridoai.domain.Embedding
import com.gridoai.domain.Document
import com.gridoai.domain.Chunk

import java.util.UUID

val mockedChunk = ChunkWithEmbedding(
  chunk = Chunk(
    uid = UUID.fromString("694b8567-8c93-45c6-8051-34be4337e740"),
    documentUid = UUID.fromString("694b8567-8c93-45c6-8051-34be4337e740"),
    documentName = "Sky observations",
    documentSource = "https://www.nasa.gov/planetarydefense/faq/asteroid",
    content = "The sky is blue",
    tokenQuantity = 4
  ),
  embedding = Embedding(vector = List(1, 2, 3), model = "mocked")
)

val mockedDocument = Document(
  uid = UUID.fromString("694b8567-8c93-45c6-8051-34be4337e740"),
  name = "Sky observations",
  source = "https://www.nasa.gov/planetarydefense/faq/asteroid",
  content = "The sky is blue",
  tokenQuantity = 4
)
