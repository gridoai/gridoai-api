package com.programandonocosmos.models
import cats.effect._
import cats.implicits.toTraverseOps
import com.programandonocosmos.adapters.*
import com.programandonocosmos.domain.*
import com.programandonocosmos.models.DocDB
import org.neo4j.cypherdsl.core.Cypher
import org.neo4j.driver.*
import org.neo4j.driver._

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.util.Try
import com.programandonocosmos.utils.|>

object Queries:
  val pageNode = Cypher.node("Page")
  val folderNode = Cypher.node("Folder")

  def newPageNode(page: Page, name: String = "p") =
    pageNode
      .named(name)
      .withProperties(
        "uid",
        Cypher.literalOf(page.id),
        "name",
        Cypher.literalOf(page.name),
        "content",
        Cypher.literalOf(page.content),
        "url",
        Cypher.literalOf(page.url)
      )

  def newFolderNode(folder: Folder, name: String = "f") =
    folderNode
      .named(name)
      .withProperties(
        "uid",
        Cypher.literalOf(folder.id),
        "name",
        Cypher.literalOf(folder.name),
        "url",
        Cypher.literalOf(folder.url)
      )
  def containsRelation(contains: Contains) =
    folderNode
      .withProperties("uid", Cypher.literalOf(contains.folderId))
      .relationshipTo(
        Cypher
          .node("Page")
          .withProperties("uid", Cypher.literalOf(contains.pageId)),
        "CONTAINS"
      )
  def addFolder(folder: Folder) =
    Cypher
      .create(newFolderNode(folder))
      .build()
      .getCypher()
  def addPage(page: Page) =
    Cypher
      .create(newPageNode(page))
      .build()
      .getCypher()
  def addContains(contains: Contains) =
    Cypher
      .merge(containsRelation(contains))
      .build()
      .getCypher()
  def getPagesByIds(ids: List[ID]) =
    val pageNodes = pageNode.named("p")
    Cypher
      .`match`(pageNodes)
      .where(
        pageNodes
          .property("uid")
          .in(Cypher.listOf(ids.map(id => Cypher.literalOf(id)).asJava))
      )
      .returning(pageNodes)
      .build()
      .getCypher()

  def addPages(pages: List[Page]) =
    Cypher
      .create(pages.map(page => newPageNode(page)).asJava)
      .build()
      .getCypher()

  def addFolders(folders: List[Folder]) =
    Cypher
      .create(folders.map(folder => newFolderNode(folder)).asJava)
      .build()
      .getCypher()

  def addContainsRelations(relations: List[(Page, Folder)]) =
    Cypher
      .create(
        relations.map { (page, folder) =>
          newFolderNode(folder)
            .relationshipTo(
              newPageNode(page),
              "CONTAINS"
            )
        }.asJava
      )
      .build()
      .getCypher()

def mapRecordToPage(record: Record) =
  Page(
    record.get("p.uid").asString(),
    record.get("p.name").asString(),
    record.get("p.content").asString(),
    record.get("p.url").asString()
  )

class Neo4j(runQuery: Neo4jQueryRunner) extends DocDB[IO]:

  def addPages(pages: List[Page]): IO[Unit] =
    pages |> Queries.addPages |> runQueryAnDiscard

  def addFolders(folders: List[Folder]): IO[Unit] =
    folders |> Queries.addFolders |> runQueryAnDiscard

  def linkPageToFolder(relation: Contains): IO[Unit] =
    relation |> Queries.addContains |> runQueryAnDiscard

  def addContainsRelations(contains: List[(Page, Folder)]): IO[Unit] =
    contains |> Queries.addContainsRelations |> runQueryAnDiscard

  def runQueryAnDiscard(query: String) = runQuery(query).void

  def addPage(page: Page) =
    page |> Queries.addPage |> runQueryAnDiscard

  def addFolder(folder: Folder) =
    folder |> Queries.addFolder |> runQueryAnDiscard

  def addContains(contains: Contains) =
    contains |> Queries.addContains |> runQueryAnDiscard

  def getPagesById(ids: List[ID]) =
    ids |> Queries.getPagesByIds |> runQuery map (_.map(mapRecordToPage))
