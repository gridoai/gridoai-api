package com.gridoai.models

import cats.data.EitherT

import com.gridoai.domain.UID
import com.gridoai.domain.ChunkWithEmbedding
import com.gridoai.domain.Embedding
import com.gridoai.domain.SimilarChunk
import com.gridoai.domain.Document
import com.gridoai.domain.PaginatedResponse
import com.gridoai.domain.Source

case class DocumentPersistencePayload(
    doc: Document,
    chunks: List[ChunkWithEmbedding]
)
trait DocDB[F[_]]:

  def addDocument(
      doc: DocumentPersistencePayload,
      orgId: String,
      role: String
  ): EitherT[F, String, Document]

  def addDocuments(
      docs: List[DocumentPersistencePayload],
      orgId: String,
      role: String
  ): EitherT[F, String, List[Document]]

  def listDocuments(
      orgId: String,
      role: String,
      start: Int,
      end: Int
  ): EitherT[F, String, PaginatedResponse[List[Document]]]

  def deleteDocument(
      uid: UID,
      orgId: String,
      role: String
  ): EitherT[F, String, Unit]

  def listDocumentsBySource(
      sources: List[Source],
      orgId: String,
      role: String
  ): EitherT[F, String, List[Document]]

  def getNearChunks(
      embeddings: List[Embedding],
      scope: Option[List[UID]],
      offset: Int,
      limit: Int,
      orgId: String,
      role: String
  ): EitherT[F, String, List[List[SimilarChunk]]]
