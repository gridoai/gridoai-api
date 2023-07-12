import cats.effect.IO
import munit.CatsEffectSuite
import doobie.implicits._
import com.gridoai.domain._
import com.gridoai.utils._
import cats.implicits._
import java.util.UUID
import com.gridoai.models.DocDB
import doobie.util.fragment.Fragment
import com.gridoai.mock

class DocumentModel extends CatsEffectSuite {
  // Create a test transactor
  import com.gridoai.models.{xa, PostgresClient, POSTGRES_SCHEMA}
  val DocsDB: DocDB[IO] = PostgresClient

  // Ensure the test schema and table are set up before each test
  override def beforeAll(): Unit = {
    (createSchema >> createTable) |> (_.unsafeRunSync()) |> println
  }
  val doc1Id = UUID.randomUUID()
  val doc2Id = UUID.randomUUID()
  val mockEmbedding = List.range(1, 769).map(_.toFloat)

  val doc = DocumentWithEmbedding(
    mock.mockedDoc.document.copy(uid = doc1Id),
    mockEmbedding
  )
  val doc2 = DocumentWithEmbedding(
    mock.mockedDoc.document.copy(uid = doc2Id),
    mockEmbedding
  )

  test("Add a document") {
    val results = List(
      DocsDB.addDocument(doc, "org1", "admin"),
      DocsDB.addDocument(doc2, "org2", "admin")
    ).parSequence

    assertIO(results, List(Right(doc1Id.toString()), Right(doc2Id.toString())))
  }

  test("Get near documents") {
    for
      maybeDocs <-
        DocsDB.getNearDocuments(doc.embedding, 10, "org1", "member")
      _ <- IO.println(maybeDocs)
      _ = assert(maybeDocs.isRight)
      docs = maybeDocs.getOrElse(List.empty)
      _ = assert(
        docs
          .forall(_.document.uid.toString() != doc2Id.toString())
      )
      _ = assert(docs.length > 0)
    yield ()
  }
  test("Deletes a document") {
    val deletionAssertions = List(
      ((doc1Id, "org2", "admin"), Left("No document was deleted")),
      ((doc2Id, "org2", "admin"), Right(())),
      ((doc1Id, "org1", "admin"), Right(()))
    )
    deletionAssertions
      .map:
        case ((docId, orgId, role), expected) =>
          val result = DocsDB.deleteDocument(docId, orgId, role)
          assertIO(result, expected, s"Failed for $docId, $orgId, $role")
      .sequence

  }

}
