package com.programandonocosmos.mock

import com.programandonocosmos.domain.Document

import java.util.UUID

val mockedDoc = Document(
  UUID.fromString("694b8567-8c93-45c6-8051-34be4337e740"),
  "Sky observations",
  "The sky is blue",
  "https://www.nasa.gov/planetarydefense/faq/asteroid",
  4
)
