package com.gridoai.models

import doobie._
import doobie.implicits._
import doobie.util.ExecutionContexts
import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import fs2.Stream

import com.gridoai.domain.*

val POSTGRES_URI = sys.env.getOrElse("POSTGRES_URI", "bolt://localhost:5432")
val POSTGRES_USER = sys.env.getOrElse("POSTGRES_USER", "postgres")
val POSTGRES_PASSWORD = sys.env.getOrElse("POSTGRES_PASSWORD", "")

val xa = Transactor.fromDriverManager[IO](
  "org.postgresql.Driver", // driver classname
  s"jdbc:postgresql:$POSTGRES_URI", // connect URL (driver-specific)
  POSTGRES_USER,
  POSTGRES_PASSWORD
)

case class Row(
    uid: UID,
    name: String,
    source: String,
    content: String,
    token_quantity: Int,
    similarity: Double
)

object PostgresClient extends DocDB[IO]:
  def addDocument(doc: DocumentWithEmbedding): IO[Unit] =
    IO.pure(())

  def getNearDocuments(
      embedding: Embedding,
      limit: Int
  ): IO[List[SimilarDocument]] =
    sql"select uid, name, source, content, token_quantity, 1 - (embedding <=> ${embedding
        .toString()}) as similarity from documents order by similarity desc limit $limit"
      .query[Row]
      .to[List]
      .transact(xa)
      .map(
        _.map(x =>
          SimilarDocument(
            document = Document(
              uid = x.uid,
              name = x.name,
              source = x.source,
              content = x.content,
              tokenQuantity = x.token_quantity
            ),
            similarity = x.similarity
          )
        )
      )

  def deleteDocument(uid: UID): IO[Unit] =
    sql"delete from documents where uid = $uid".update.run
      .transact(xa)
      .map(_ => ())
