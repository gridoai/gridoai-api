package com.programandonocosmos.domain
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

type ID = String

case class Folder(id: ID, name: String, url: String)
case class Page(id: ID, name: String, content: String, url: String)
case class Contains(folderId: ID, pageId: ID)
