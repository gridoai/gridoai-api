package com.gridoai.mock

import com.gridoai.domain.DocumentWithEmbedding
import com.gridoai.domain.Document

import java.util.UUID

val mockedDoc = DocumentWithEmbedding(
  document = Document(
    uid = UUID.fromString("694b8567-8c93-45c6-8051-34be4337e740"),
    name = "Sky observations",
    source = "https://www.nasa.gov/planetarydefense/faq/asteroid",
    content = "The sky is blue",
    tokenQuantity = 4
  ),
  embedding = List(1, 2, 3)
)
