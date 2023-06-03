package com.programandonocosmos.models

import cats.effect.IO
import com.programandonocosmos.domain._
import com.programandonocosmos.mock.mockedDoc

import java.util.UUID
import scala.collection.mutable.ListBuffer

object MockDocDB extends DocDB[IO]:
  private val documents = ListBuffer[Document](
    mockedDoc
  )
  private val mentions = ListBuffer[Mentions]()

  def addDocument(doc: Document): IO[Unit] = IO.pure {
    documents += doc
    println(s"Mock: Adding document $doc")
  }

  def addMentions(mention: Mentions): IO[Unit] = IO.pure {
    mentions += mention
    println(s"Mock: Adding mentions $mention")
  }
  def getDocumentsByIds(ids: List[UID]): IO[List[Document]] = IO.pure {
    documents.toList.filter(doc => ids.contains(doc.uid))
  }
  def getDocumentById(id: UID): IO[Document] = IO.pure {
    documents
      .find(_.uid == id)
      .getOrElse(throw new Exception("Document not found"))
  }
  def getDocumentMentions(id: UID): IO[List[Document]] = IO.pure {
    val mentionedIds = mentions.filter(_.from == id).map(_.to).toList
    documents.filter(doc => mentionedIds.contains(doc.uid)).toList
  }
