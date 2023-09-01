package com.gridoai.models

import doobie.*
import doobie.implicits.*
import cats.*
import cats.effect.*
import cats.syntax.list.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import com.pgvector.PGvector

import com.gridoai.domain.*
import com.gridoai.utils.*
case class DocRow(
    uid: UID,
    name: String,
    source: String,
    content: String,
    organization: String,
    roles: Array[String]
)
extension (x: Document)
  def toDocRow(orgId: String, role: String) =
    DocRow(
      uid = x.uid,
      name = x.name,
      source = x.source.toString,
      content = x.content,
      organization = orgId,
      roles = Array(role)
    )

extension (x: DocRow)
  def toDocument: Either[String, Document] =
    strToSource(x.source)
      .map: s =>
        Document(
          uid = x.uid,
          name = x.name,
          source = s,
          content = x.content
        )

case class ChunkRow(
    uid: UID,
    document_uid: UID,
    document_name: String,
    document_source: String,
    content: String,
    start_pos: Int,
    end_pos: Int,
    embedding: List[Float],
    embedding_model: EmbeddingModel,
    token_quantity: Int,
    document_organization: String,
    document_roles: List[String]
)

extension (x: ChunkWithEmbedding)
  def toChunkRow(role: String, orgId: String) =
    ChunkRow(
      uid = x.chunk.uid,
      document_uid = x.chunk.documentUid,
      document_name = x.chunk.documentName,
      document_source = x.chunk.documentSource.toString,
      content = x.chunk.content,
      start_pos = x.chunk.startPos,
      end_pos = x.chunk.endPos,
      embedding = x.embedding.vector,
      embedding_model = x.embedding.model,
      token_quantity = x.chunk.tokenQuantity,
      document_organization = orgId,
      document_roles = List(role)
    )

case class NearChunkOutput(
    uid: UID,
    document_uid: UID,
    document_name: String,
    document_source: String,
    content: String,
    start_pos: Int,
    end_pos: Int,
    token_quantity: Int,
    distance: Float
)

extension (x: NearChunkOutput)
  def toChunk: Either[String, SimilarChunk] =
    strToSource(x.document_source)
      .map: s =>
        SimilarChunk(
          chunk = Chunk(
            documentUid = x.document_uid,
            documentName = x.document_name,
            documentSource = s,
            uid = x.uid,
            content = x.content,
            tokenQuantity = x.token_quantity,
            startPos = x.start_pos,
            endPos = x.end_pos
          ),
          distance = x.distance
        )

implicit val getPGvector: Get[PGvector] =
  Get[Array[Float]].map(new PGvector(_))
implicit val putPGvector: Put[PGvector] =
  Put[Array[Float]].tcontramap(_.toArray())
implicit val getEmbeddingModel: Get[EmbeddingModel] =
  Get[String].map(strToEmbedding(_))
implicit val putEmbeddingModel: Put[EmbeddingModel] =
  Put[String].tcontramap(embeddingToStr(_))

val POSTGRES_HOST =
  sys.env.getOrElse("POSTGRES_HOST", "localhost")
val POSTGRES_PORT =
  sys.env.getOrElse("POSTGRES_PORT", "5432")
val POSTGRES_DATABASE =
  sys.env.getOrElse("POSTGRES_DATABASE", "gridoai")
val POSTGRES_USER = sys.env.getOrElse("POSTGRES_USER", "postgres")
val POSTGRES_PASSWORD = sys.env.getOrElse("POSTGRES_PASSWORD", "")
val POSTGRES_SCHEMA = sys.env.getOrElse("POSTGRES_SCHEMA", "public")

def pgObj(name: String) = Fragment.const(s"$POSTGRES_SCHEMA.${name}")
val documentsTable = pgObj("documents")
val chunksTable = pgObj("chunks")
val EmbeddingModelEnum = pgObj("embedding_model")

object PostgresClient {
  def apply[F[_]: Async](implicit
      lh: doobie.LogHandler = doobie.LogHandler.nop
  ): DocDB[F] = new DocDB[F] {
    import cats.implicits._

    val xa = Transactor.fromDriverManager[F](
      "org.postgresql.Driver",
      s"jdbc:postgresql://$POSTGRES_HOST:$POSTGRES_PORT/$POSTGRES_DATABASE",
      POSTGRES_USER,
      POSTGRES_PASSWORD
    )

    def addDocument(
        doc: DocumentPersistencePayload,
        orgId: String,
        role: String
    ) =
      addDocuments(List(doc), orgId, role).mapRight(_.head)

    def addDocuments(
        docs: List[DocumentPersistencePayload],
        orgId: String,
        role: String
    ): F[Either[String, List[Document]]] =
      docs.toNel match
        case Some(documents) =>
          val documentRows = documents.map(_.doc.toDocRow(orgId, role))
          val chunkRows =
            documents.toList.flatMap(_.chunks.map(_.toChunkRow(role, orgId)))
          val uids = documentRows.map(_.uid)

          (for
            _ <-
              sql"delete from $chunksTable where ${Fragments.in(fr"document_uid", uids)} and document_organization = ${orgId} ".update.run
            _ <-
              sql"delete from $documentsTable where ${Fragments.in(fr"uid", uids)} and organization = ${orgId}".update.run
            _ <- Update[DocRow](
              s"""insert into $POSTGRES_SCHEMA.documents (uid, name, source, content, organization, roles) 
                  values (?, ?, ?, ?, ?, ?)"""
            ).updateMany(documentRows)
            _ <- Update[ChunkRow](
              s"""insert into $POSTGRES_SCHEMA.chunks (
                  uid,
                  document_uid,
                  document_name,
                  document_source,
                  content,
                  start_pos,
                  end_pos,
                  embedding,
                  embedding_model,
                  token_quantity,
                  document_organization,
                  document_roles
                ) values (
                  ?, ?, ?, ?, ?, ?, ?, ?, ?::$POSTGRES_SCHEMA.embedding_model, ?, ?, ?
                )"""
            ).updateMany(chunkRows)
          yield docs.map(_.doc)).transact[F](xa).map(Right(_)) |> attempt
        case None => Right(List()).pure[F]

    def listDocuments(
        orgId: String,
        role: String,
        start: Int,
        end: Int
    ): F[Either[String, PaginatedResponse[List[Document]]]] =
      traceMappable("listDocuments"):
        sql"""
       select uid, name, source, content, organization, roles, count(*) over() as total_count 
       from $documentsTable 
       where organization = ${orgId} 
       order by uid asc 
       limit ${(end - start).abs} 
       offset ${start}
     """
          .query[(DocRow, Int)]
          .to[List]
          .transact[F](xa)
          .map(results =>
            val totalCount = results.headOption.map(_._2).getOrElse(0)
            results
              .traverse(_._1.toDocument)
              .map: documents =>
                PaginatedResponse(documents, totalCount)
          ) |> attempt

    def deleteDocument(
        uid: UID,
        orgId: String,
        role: String
    ): F[Either[String, Unit]] =
      (for
        deletedChunks <-
          sql"delete from $chunksTable where document_uid = $uid and document_organization = ${orgId} ".update.run
        deletedDocuments <-
          sql"delete from $documentsTable where uid = $uid and organization = ${orgId}".update.run
      yield (deletedChunks, deletedDocuments))
        .transact[F](xa)
        .map((deletedChunks, deletedDocuments) =>
          if (deletedDocuments == 0) Left("No document was deleted")
          else if (deletedChunks == 0) Left("No chunk was deleted")
          else Right(())
        ) |> attempt

    def listDocumentsBySource(
        sources: List[Source],
        orgId: String,
        role: String
    ): F[Either[String, List[Document]]] =
      sources.map(_.toString).toNel match
        case Some(sourceStrings) =>
          sql"""
            select uid, name, source, content, organization, roles
            from $documentsTable 
            where organization = ${orgId} and ${Fragments.in(
              fr"source",
              sourceStrings
            )}
          """
            .query[DocRow]
            .to[List]
            .transact[F](xa)
            .map(_.traverse(_.toDocument)) |> attempt
        case None => Right(List()).pure[F]

    def getNearChunks(
        embedding: Embedding,
        scope: Option[List[UID]],
        offset: Int,
        limit: Int,
        orgId: String,
        role: String
    ): F[Either[String, List[SimilarChunk]]] =
      traceMappable("getNearDocuments"):
        println("Getting near docs ")
        val vector = PGvector(embedding.vector.toArray)

        val BaseQuery =
          sql"""select
            uid,
            document_uid,
            document_name,
            document_source,
            content,
            start_pos,
            end_pos,
            token_quantity,
            embedding <=> $vector::vector as distance
          from $chunksTable
          where
            document_organization = $orgId and
            embedding_model = ${embedding.model}::$EmbeddingModelEnum"""

        val scopeFilter = scope.flatMap(_.toNel) match
          case Some(uids) =>
            fr"and ${Fragments.in(fr"document_uid", uids)}"
          case _ => fr""

        val queryPagination = fr"""
          order by distance asc
          offset $offset
          limit $limit"""

        (BaseQuery ++ scopeFilter ++ queryPagination)
          .query[NearChunkOutput]
          .to[List]
          .transact[F](xa)
          .map(_.traverse(_.toChunk))
          |> attempt

  }
}
