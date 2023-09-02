package com.gridoai.scripts.reprocChunks

import com.gridoai.models.*
import com.gridoai.domain.*
import com.gridoai.utils.*
import com.gridoai.services.doc.*
import com.gridoai.adapters.embeddingApi.*

import doobie.*
import doobie.implicits.*
import cats.*
import cats.effect.*
import cats.implicits.*
import cats.syntax.list.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import cats.effect.unsafe.implicits.global
import com.pgvector.PGvector

// get all doc uids, name, content, source, orgId, role
// for each doc
//      upsert doc
def reproc() =
  val xa = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    s"jdbc:postgresql://$POSTGRES_HOST:$POSTGRES_PORT/$POSTGRES_DATABASE",
    POSTGRES_USER,
    POSTGRES_PASSWORD
  )
  val docDb = PostgresClient[IO]
  sql"""
       select uid, name, source, content, organization, roles
       from $documentsTable
       order by uid asc
     """
    .query[DocRow]
    .to[List]
    .transact[IO](xa)
    .unsafeRunSync()
    .traverse(docRow =>
      docRow.toDocument.map(doc => (doc, docRow.organization, docRow.roles))
    ) match
    case Right(docs) =>
      println(docs.map(_._1.name).mkString("\n"))
      docs.map: (doc, org, roles) =>
        mapDocumentsToDB(List(doc), getEmbeddingAPI("embaas"))
          .flatMapRight(persistencePayload =>
            println("Got persistencePayloads: " + persistencePayload.length)
            docDb.addDocuments(
              persistencePayload,
              org,
              roles.head
            )
          )
          .unsafeRunSync()
