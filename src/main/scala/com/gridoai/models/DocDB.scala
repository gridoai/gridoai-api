package com.gridoai.models

import com.gridoai.domain.UID
import com.gridoai.domain.Embedding
import com.gridoai.domain.DocumentWithEmbedding
import com.gridoai.domain.SimilarDocument

trait DocDB[F[_]]:
  def addDocument(doc: DocumentWithEmbedding): F[Either[String, Unit]]
  def getNearDocuments(
      embedding: Embedding,
      limit: Int
  ): F[Either[String, List[SimilarDocument]]]
  def deleteDocument(uid: UID): F[Either[String, Unit]]
