package com.gridoai.test
import cats.data.EitherT
implicit def eitherTisIO[A, B](eitherT: EitherT[IO, A, B]): IO[Either[A, B]] =
  eitherT.value
import cats.effect.IO
import munit.CatsEffectSuite
import doobie.implicits._
import com.gridoai.domain._
import com.gridoai.models._

import com.gridoai.utils._
import cats.implicits._
import java.util.UUID
import com.gridoai.models.DocDB
import com.gridoai.mock

class DocumentModel extends CatsEffectSuite {
  // Create a test transactor
  import com.gridoai.models.PostgresClient
  given doobie.LogHandler[IO] = doobie.LogHandler.jdkLogHandler
  val DocsDB: DocDB[IO] = PostgresClient[IO](PostgresClient.getSyncTransactor)

  val doc1Id = UUID.randomUUID()
  val doc2Id = UUID.randomUUID()
  val doc3Id = UUID.randomUUID()
  val mockEmbedding =
    Embedding(
      vector = List.range(1, 768).map(_.toFloat),
      model = EmbeddingModel.Mocked
    )

  val doc1 = mock.mockedDocument.copy(uid = doc1Id)
  val doc2 = mock.mockedDocument.copy(uid = doc2Id)
  val doc3 = mock.mockedDocument.copy(uid = doc3Id)

  test("Add a document") {
    val results = List(
      DocsDB.addDocuments(
        List(
          DocumentPersistencePayload(
            doc1,
            List(
              ChunkWithEmbedding(
                chunk = Chunk(
                  documentUid = doc1.uid,
                  documentName = doc1.name,
                  documentSource = doc1.source,
                  uid = UUID.randomUUID(),
                  content = doc1.content,
                  tokenQuantity = 4,
                  startPos = 0,
                  endPos = 0
                ),
                embedding = mockEmbedding
              )
            )
          )
        ),
        "org1",
        "admin"
      ),
      DocsDB.addDocuments(
        List(
          DocumentPersistencePayload(
            doc2,
            List(
              ChunkWithEmbedding(
                chunk = Chunk(
                  documentUid = doc2.uid,
                  documentName = doc2.name,
                  documentSource = doc2.source,
                  uid = UUID.randomUUID(),
                  content = doc2.content,
                  tokenQuantity = 4,
                  startPos = 0,
                  endPos = 0
                ),
                embedding = mockEmbedding
              )
            )
          )
        ),
        "org2",
        "admin"
      ),
      DocsDB.addDocuments(
        List(
          DocumentPersistencePayload(
            doc3,
            List(
              ChunkWithEmbedding(
                chunk = Chunk(
                  documentUid = doc3.uid,
                  documentName = doc3.name,
                  documentSource = doc3.source,
                  uid = UUID.randomUUID(),
                  content = doc3.content,
                  tokenQuantity = 4,
                  startPos = 0,
                  endPos = 0
                ),
                embedding = mockEmbedding
              )
            )
          )
        ),
        "org2",
        "admin"
      )
    ).parSequence

    results.value.map: v =>
      assert(v == List(Right(List(doc1)), Right(List(doc2)), Right(List(doc3))))

  }

  test("Get near chunks") {
    for
      maybeChunks <-
        DocsDB.getNearChunks(List(mockEmbedding), None, 0, 10, "org1", "member")
      _ = println(maybeChunks)
      _ = assert(!maybeChunks.isEmpty)
      chunks = maybeChunks
      _ = assert(
        chunks.head.forall(_.chunk.uid.toString() != doc2Id.toString())
      )
      _ = assert(chunks.length > 0)
    yield ()
  }
  test("Get near chunks with scope") {
    for
      maybeChunks <-
        DocsDB.getNearChunks(
          List(mockEmbedding),
          Some(List(doc3Id)),
          0,
          10,
          "org2",
          "member"
        )
      _ = println(maybeChunks)
      _ = assert(!maybeChunks.isEmpty)
      chunks = maybeChunks
      _ = assert(
        chunks.head.forall(_.chunk.uid.toString() != doc2Id.toString())
      )
      _ = assert(chunks.length > 0)
    yield ()
  }
  test("Deletes a document") {
    val deletionAssertions = List(
      (
        (doc1Id, "org2", "admin"),
        Left(
          "No document was deleted"
        )
      ),
      ((doc2Id, "org2", "admin"), Right(())),
      ((doc1Id, "org1", "admin"), Right(()))
    )
    deletionAssertions
      .map:
        case ((docId, orgId, role), expected) =>
          val result = DocsDB.deleteDocument(docId, orgId, role)
          assertIO(
            result.leftMap(_.split(" ", 2).last).value,
            expected,
            s"Failed for $docId, $orgId, $role"
          )
      .sequence

  }

}
