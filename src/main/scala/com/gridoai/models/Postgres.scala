package com.gridoai.models

import doobie.*
import doobie.implicits.*
import cats.*
import cats.effect.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import com.pgvector.PGvector

import com.gridoai.domain.*
import com.gridoai.utils.*

implicit val getPGvector: Get[PGvector] =
  Get[String].tmap(new PGvector(_))
implicit val putPGvector: Put[PGvector] =
  Put[String].tcontramap(x => s"'${x.toArray()}'::vector")

val POSTGRES_URI = sys.env.getOrElse("POSTGRES_URI", "//localhost:5432/gridoai")
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
    distance: Float
)

object PostgresClient extends DocDB[IO]:
  def addDocument(doc: DocumentWithEmbedding): IO[Either[String, Unit]] =
    IO.pure(Right(()))

  def getNearDocuments(
      embedding: Embedding,
      limit: Int
  ): IO[Either[String, List[SimilarDocument]]] =

    val vector = PGvector(embedding.toArray)
    val query =
      sql"select uid, name, source, content, token_quantity, embedding <-> '$vector'::vector as distance from documents order by distance asc limit $limit"
    println(query.toString())
    query
      .queryWithLogHandler[Row](LogHandler.jdkLogHandler)
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
            distance = x.distance
          )
        )
      )
      .map((Right(_))) |> attempt

  def deleteDocument(uid: UID): IO[Either[String, Unit]] =
    sql"delete from documents where uid = $uid".update.run
      .transact(xa)
      .map(_ => Right(())) |> attempt
