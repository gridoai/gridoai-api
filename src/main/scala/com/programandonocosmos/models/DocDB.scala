package com.programandonocosmos.models

import com.programandonocosmos.domain.Document
import com.programandonocosmos.domain.Mentions
import com.programandonocosmos.domain.UID

trait DocDB[F[_]]:
  def addDocument(doc: Document): F[Unit]
  def addMentions(mentions: Mentions): F[Unit]
  def getDocumentsByIds(ids: List[UID]): F[List[Document]]
  def getDocumentById(id: UID): F[Document]
  def getDocumentMentions(id: UID): F[List[Document]]
