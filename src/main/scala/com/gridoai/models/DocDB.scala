package com.gridoai.models

import com.gridoai.domain.UID
import com.gridoai.domain.ChunkWithEmbedding
import com.gridoai.domain.SimilarChunk
import com.gridoai.domain.Document
import com.gridoai.domain.PaginatedResponse
import com.gridoai.domain.Chunk

trait DocDB[F[_]]:
  def addDocument(
      doc: Document,
      orgId: String,
      role: String
  ): F[Either[String, Document]]

  def listDocuments(
      orgId: String,
      role: String,
      start: Int,
      end: Int
  ): F[Either[String, PaginatedResponse[List[Document]]]]

  def deleteDocument(
      uid: UID,
      orgId: String,
      role: String
  ): F[Either[String, Unit]]

  def addChunks(orgId: String, role: String)(
      chunks: List[ChunkWithEmbedding]
  ): F[Either[String, List[ChunkWithEmbedding]]]

  def getNearChunks(
      embedding: List[Float],
      limit: Int,
      orgId: String,
      role: String
  ): F[Either[String, List[SimilarChunk]]]

  def deleteChunks(
      uid: List[UID],
      orgId: String,
      role: String
  ): F[Either[String, Unit]]
