package com.gridoai.models
import cats.Apply.ops.toAllApplyOps
import cats.effect._
import cats.implicits.toTraverseOps
import com.gridoai.adapters.*
import com.gridoai.domain.*
import com.gridoai.models.DocDB
import com.gridoai.utils.|>
import org.neo4j.cypherdsl.core.Cypher
import org.neo4j.cypherdsl.core.Node
import org.neo4j.driver.*
import org.neo4j.driver._

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.util.Try

object Queries:
  val documentTypeName = "Document"
  val documentNode = Cypher.node(documentTypeName)
  val mentionsRelationshipName = "MENTIONS"

  def mentionsRelationship(from: Node, to: Node) =
    from.relationshipTo(to, mentionsRelationshipName)

  def addDocument(page: Document) =
    Cypher
      .create(
        documentNode.withProperties(
          "uid",
          Cypher.literalOf(page.uid.toString()),
          "name",
          Cypher.literalOf(page.name),
          "content",
          Cypher.literalOf(page.content),
          "url",
          Cypher.literalOf(page.url),
          "numberOfWords",
          Cypher.literalOf(page.numberOfWords)
        )
      )
      .build()
      .getCypher()

  def addMentions(mentions: Mentions) =
    val from = documentNode.named("from")
    val to = documentNode.named("to")
    Cypher
      .`match`(from)
      .where(
        from
          .property("uid")
          .isEqualTo(Cypher.literalOf(mentions.from.toString()))
      )
      .`match`(to)
      .where(
        to.property("uid").isEqualTo(Cypher.literalOf(mentions.to.toString()))
      )
      .create(mentionsRelationship(from, to))
      .build()
      .getCypher()

  def getDocumentsByIds(ids: List[UID]) =
    val nodes = documentNode.named("doc")
    Cypher
      .`match`(nodes)
      .where(
        nodes
          .property("uid")
          .in(
            Cypher.listOf(ids.map(id => Cypher.literalOf(id.toString())).asJava)
          )
      )
      .returning(nodes)
      .build()
      .getCypher()

  def getDocumentById(id: UID) =
    val node = documentNode.named("doc")
    Cypher
      .`match`(node)
      .where(node.property("uid").isEqualTo(Cypher.literalOf(id.toString())))
      .returning(node)
      .build()
      .getCypher()

  def getDocumentMentions(id: UID) =
    val from = documentNode
      .named("from")

    val to = documentNode.named("to")
    Cypher
      .`match`(
        mentionsRelationship(
          from.withProperties("uid", Cypher.literalOf(id.toString())),
          to
        )
      )
      .returning(to)
      .build()
      .getCypher()

def mapToDocument(r: Record): Document =
  val node = r.get("doc").asNode()
  Document(
    UUID.fromString(node.get("uid").asString()),
    node.get("name").asString(),
    node.get("content").asString(),
    node.get("url").asString(),
    node.get("numberOfWords").asInt()
  )

class Neo4j(runQuery: Neo4jQueryRunner) extends DocDB[IO]:
  def addDocument(page: Document): IO[Unit] =
    Queries.addDocument(page) |> runQuery map (_._2.void)

  def addMentions(contains: Mentions): IO[Unit] =
    Queries.addMentions(contains) |> runQuery map (_._2.void)

  def getDocumentsByIds(ids: List[UID]): IO[List[Document]] =
    Queries.getDocumentsByIds(ids) |> runQuery map (_._2.map(mapToDocument))

  def getDocumentById(id: UID): IO[Document] =
    Queries.getDocumentById(id) |> runQuery map (_._2.map(mapToDocument).head)

  def getDocumentMentions(id: UID): IO[List[Document]] =
    Queries.getDocumentMentions(id) |> runQuery map (_._2.map(mapToDocument))
