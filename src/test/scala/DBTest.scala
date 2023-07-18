import cats.effect.IO
import munit.CatsEffectSuite
import doobie.implicits._
import com.gridoai.domain._
import com.gridoai.utils._
import cats.implicits._
import java.util.UUID
import com.gridoai.models.DocDB
import com.gridoai.mock

class DocumentModel extends CatsEffectSuite {
  // Create a test transactor
  import com.gridoai.models.PostgresClient
  val DocsDB: DocDB[IO] = PostgresClient[IO]

  val doc1Id = UUID.randomUUID()
  val doc2Id = UUID.randomUUID()
  val mockEmbedding =
    Embedding(
      vector = List.range(1, 769).map(_.toFloat),
      model = EmbeddingModel.Mocked
    )

  val doc = mock.mockedDocument.copy(uid = doc1Id)
  val doc2 = mock.mockedDocument.copy(uid = doc2Id)

  test("Add a document") {
    val results = List(
      DocsDB.addDocument(doc, "org1", "admin"),
      DocsDB.addDocument(doc2, "org2", "admin")
    ).parSequence

    assertIO(results, List(Right(doc), Right(doc2)))
  }

  test("Get near chunks") {
    for
      maybeChunks <-
        DocsDB.getNearChunks(mockEmbedding, 0, 10, "org1", "member")
      _ <- IO.println(maybeChunks)
      _ = assert(maybeChunks.isRight)
      chunks = maybeChunks.getOrElse(List.empty)
      _ = assert(
        chunks
          .forall(_.chunk.uid.toString() != doc2Id.toString())
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
            result.mapLeft(_.split(" ", 2).last),
            expected,
            s"Failed for $docId, $orgId, $role"
          )
      .sequence

  }

}
