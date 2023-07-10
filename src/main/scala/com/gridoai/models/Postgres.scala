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

case class Row(
    uid: UID,
    name: String,
    source: String,
    content: String,
    token_quantity: Int,
    distance: Float
)

implicit val getPGvector: Get[PGvector] =
  Get[Array[Float]].map(new PGvector(_))
implicit val putPGvector: Put[PGvector] =
  Put[Array[Float]].tcontramap(_.toArray())

val POSTGRES_URI =
  sys.env.getOrElse("POSTGRES_URI", "//localhost:5432/gridoai")
val POSTGRES_USER = sys.env.getOrElse("POSTGRES_USER", "postgres")
val POSTGRES_PASSWORD = sys.env.getOrElse("POSTGRES_PASSWORD", "")
val POSTGRES_SCHEMA = sys.env.getOrElse("POSTGRES_SCHEMA", "public")

val xa = Transactor.fromDriverManager[IO](
  "org.postgresql.Driver", // driver classname
  s"jdbc:postgresql:$POSTGRES_URI", // connect URL (driver-specific)
  POSTGRES_USER,
  POSTGRES_PASSWORD
)

def table(name: String) = Fragment.const(s"$POSTGRES_SCHEMA.${name}")
val documentsTable = table("documents")

object PostgresClient extends DocDB[IO]:

  def addDocument(
      doc: DocumentWithEmbedding,
      orgId: String,
      role: String
  ): IO[Either[String, String]] =
    sql"""insert into $documentsTable (uid, name, source, content, token_quantity, embedding, organization, roles) 
     values (
      ${doc.document.uid},
      ${doc.document.name},
      ${doc.document.source},
      ${doc.document.content},
      ${doc.document.tokenQuantity},
      ${doc.embedding},
      ${orgId},
      ${Array(role)}

    )""".update.run
      .transact(xa)
      .map(_ => Right(doc.document.uid.toString())) |> attempt

  def listDocuments(
      orgId: String,
      role: String,
      start: Int,
      end: Int
  ): IO[Either[String, List[Document]]] =
    traceMappable("listDocuments"):
      sql"select uid, name, source, content, token_quantity from $documentsTable where organization = ${orgId} order by uid asc limit ${(end - start).abs} offset ${start}"
        .query[Document]
        .to[List]
        .transact(xa)
        .map(Right(_)) |> attempt

  def getNearDocuments(
      embedding: Embedding,
      limit: Int,
      orgId: String,
      role: String
  ): IO[Either[String, List[SimilarDocument]]] =
    traceMappable("getNearDocuments"):
      println("Getting near docs ")
      val vector = PGvector(embedding.toArray)
      val query =
        sql"select uid, name, source, content, token_quantity, embedding <-> $vector::vector as distance from $documentsTable where organization = $orgId  order by distance asc limit $limit"
      query
        .query[Row]
        .to[List]
        .transact(xa)
        .map(
          _.map(x =>
            SimilarDocument(
              document = Document(
                x.uid,
                x.name,
                x.source,
                x.content,
                x.token_quantity
              ),
              distance = x.distance
            )
          )
        )
        .map((Right(_))) |> attempt

  def deleteDocument(
      uid: UID,
      orgId: String,
      role: String
  ): IO[Either[String, Unit]] =
    sql"delete from $documentsTable where uid = $uid and organization = ${orgId}".update.run
      .transact(xa)
      .flatMap(r =>
        if (r > 0)
          IO.pure(Right(()))
        else
          IO.pure(Left("No document was deleted"))
      )
      |> attempt
