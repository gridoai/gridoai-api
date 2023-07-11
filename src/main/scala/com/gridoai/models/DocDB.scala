package com.gridoai.models

import com.gridoai.domain.UID
import com.gridoai.domain.Embedding
import com.gridoai.domain.DocumentWithEmbedding
import com.gridoai.domain.SimilarDocument
import com.gridoai.domain.Document

trait DocDB[F[_]]:
  def addDocument(
      doc: DocumentWithEmbedding,
      orgId: String,
      role: String
  ): F[Either[String, String]]

  def getNearDocuments(
      embedding: Embedding,
      limit: Int,
      orgId: String,
      role: String
  ): F[Either[String, List[SimilarDocument]]]

  def deleteDocument(
      uid: UID,
      orgId: String,
      role: String
  ): F[Either[String, Unit]]

  def listDocuments(
      orgId: String,
      role: String,
      start: Int,
      end: Int
  ): F[Either[String, (List[Document], Int)]]
