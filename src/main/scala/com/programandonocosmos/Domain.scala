package com.programandonocosmos.domain
import org.neo4j.ogm.annotation.EndNode
import org.neo4j.ogm.annotation.GeneratedValue
import org.neo4j.ogm.annotation.Id
import org.neo4j.ogm.annotation.NodeEntity
import org.neo4j.ogm.annotation.Property
import org.neo4j.ogm.annotation.Relationship
import org.neo4j.ogm.annotation.Relationship.Direction
import org.neo4j.ogm.annotation.RelationshipEntity
import org.neo4j.ogm.annotation.StartNode
import org.neo4j.ogm.session.Session

import java.util.UUID

type ID = String
type UID = UUID

@NodeEntity
case class Document(
    @Id @GeneratedValue
    id: ID,
    @Property(name = "uid")
    uid: UID,
    @Property(name = "name")
    name: String,
    @Property(name = "content")
    content: String,
    @Property(name = "url")
    url: String,
    @Property(name = "numberOfWords")
    numberOfWords: Int,
    @Relationship(`type` = "MENTIONS", direction = Direction.OUTGOING)
    mentions: List[Mentions] = List.empty[Mentions]
)

@RelationshipEntity(`type` = "MENTIONS")
case class Mentions(
    @Id @GeneratedValue
    relationshipId: Long,
    @StartNode
    from: Document,
    @EndNode
    to: Document
)
