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

case class ChunkRow(
    uid: UID,
    document_uid: UID,
    document_name: String,
    document_source: String,
    content: String,
    embedding: List[Float],
    embedding_model: EmbeddingModel,
    token_quantity: Int,
    document_organization: String,
    document_roles: List[String]
)

implicit val getPGvector: Get[PGvector] =
  Get[Array[Float]].map(new PGvector(_))
implicit val putPGvector: Put[PGvector] =
  Put[Array[Float]].tcontramap(_.toArray())
implicit val getEmbeddingModel: Get[EmbeddingModel] =
  Get[String].map(strToEmbedding(_))
implicit val putEmbeddingModel: Put[EmbeddingModel] =
  Put[String].tcontramap(embeddingToStr(_))

val POSTGRES_URI =
  sys.env.getOrElse("POSTGRES_URI", "//localhost:5432/gridoai")
val POSTGRES_USER = sys.env.getOrElse("POSTGRES_USER", "postgres")
val POSTGRES_PASSWORD = sys.env.getOrElse("POSTGRES_PASSWORD", "")
val POSTGRES_SCHEMA = sys.env.getOrElse("POSTGRES_SCHEMA", "public")

def table(name: String) = Fragment.const(s"$POSTGRES_SCHEMA.${name}")
val documentsTable = table("documents")
val chunksTable = table("chunks")
object PostgresClient {

  def apply[F[_]: Async]: DocDB[F] = new DocDB[F] {
    import cats.implicits._
    val xa = Transactor.fromDriverManager[F](
      "org.postgresql.Driver", // driver classname
      s"jdbc:postgresql:$POSTGRES_URI", // connect URL (driver-specific)
      POSTGRES_USER,
      POSTGRES_PASSWORD
    )

    def addDocument(
        doc: Document,
        orgId: String,
        role: String
    ): F[Either[String, Document]] =
      sql"""insert into $documentsTable (uid, name, source, content, organization, roles) 
       values (
        ${doc.uid},
        ${doc.name},
        ${doc.source},
        ${doc.content},
        ${orgId},
        ${Array(role)}
      )""".update.run
        .transact[F](xa)
        .map(_ => Right(doc)) |> attempt

    def listDocuments(
        orgId: String,
        role: String,
        start: Int,
        end: Int
    ): F[Either[String, PaginatedResponse[List[Document]]]] =
      traceMappable("listDocuments"):
        sql"""
       select uid, name, source, content, count(*) over() as total_count 
       from $documentsTable 
       where organization = ${orgId} 
       order by uid asc 
       limit ${(end - start).abs} 
       offset ${start}
     """
          .query[(Document, Int)]
          .to[List]
          .transact[F](xa)
          .map(results =>
            val totalCount = results.headOption.map(_._2).getOrElse(0)
            Right(PaginatedResponse(results.map(_._1), totalCount))
          ) |> attempt

    def deleteDocument(
        uid: UID,
        orgId: String,
        role: String
    ): F[Either[String, Unit]] =
      sql"delete from $documentsTable where uid = $uid and organization = ${orgId}".update.run
        .transact(xa)
        .flatMap(r =>
          if (r > 0)
            (Right(())).pure[F]
          else
            (Left("No document was deleted")).pure[F]
        ) |> attempt

    def addChunks(orgId: String, role: String)(
        chunks: List[ChunkWithEmbedding]
    ): F[Either[String, List[ChunkWithEmbedding]]] =
      val rows = chunks.map(x =>
        ChunkRow(
          uid = x.chunk.uid,
          document_uid = x.chunk.documentUid,
          document_name = x.chunk.documentName,
          document_source = x.chunk.documentSource,
          content = x.chunk.content,
          embedding = x.embedding.vector,
          embedding_model = x.embedding.model,
          token_quantity = x.chunk.tokenQuantity,
          document_organization = orgId,
          document_roles = List(role)
        )
      )
      Update[ChunkRow](
        s"""insert into chunks(
        uid,
        document_uid,
        document_name,
        document_source,
        content,
        embedding,
        embedding_model,
        token_quantity,
        document_organization,
        document_roles
      ) values (
        ?, ?, ?, ?, ?, ?, ?::embedding_model, ?, ?, ?
      )"""
      )
        .updateMany(rows)
        .transact[F](xa)
        .map(_ => Right(chunks)) |> attempt

    def getNearChunks(
        embedding: Embedding,
        offset: Int,
        limit: Int,
        orgId: String,
        role: String
    ): F[Either[String, List[SimilarChunk]]] =
      traceMappable("getNearDocuments"):
        println("Getting near docs ")
        val vector = PGvector(embedding.vector.toArray)
        val query =
          sql"""select
            document_uid,
            document_name,
            document_source,
            uid,
            content,
            token_quantity,
            embedding <-> $vector::vector as distance
          from $chunksTable
          where
            document_organization = $orgId and
            embedding_model = ${embedding.model}::embedding_model
          order by distance asc
          offset $offset
          limit $limit"""
        query
          .query[(Chunk, Float)]
          .to[List]
          .transact[F](xa)
          .map(
            _.map(x =>
              SimilarChunk(
                chunk = x(0),
                distance = x(1)
              )
            )
          )
          .map((Right(_))) |> attempt

    def deleteChunksByDocument(
        documentUid: UID,
        orgId: String,
        role: String
    ): F[Either[String, Unit]] =
      sql"delete from $chunksTable where document_uid = $documentUid and organization = ${orgId}".update.run
        .transact(xa)
        .flatMap(r =>
          if (r > 0)
            (Right(())).pure[F]
          else
            (Left("No chunk was deleted")).pure[F]
        ) |> attempt
  }
}
