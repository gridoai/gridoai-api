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
  // Create a test schema if it doesn't exist
  val createSchema =
    sql"CREATE SCHEMA IF NOT EXISTS ${Fragment.const(POSTGRES_SCHEMA)}".update.run
      .transact(xa)
  // Create a test table if it doesn't exist
  val createTable = sql"""
    CREATE TABLE IF NOT EXISTS ${Fragment.const(POSTGRES_SCHEMA)}.documents (
      uid uuid not null default uuid_generate_v4 (),
      name text not null,
      source text not null,
      content text not null,
      embedding public.vector not null,
      token_quantity integer not null,
      organization text null,
      roles text[] not null default '{}'::text[],
      constraint documents_pkey primary key (uid)
    )
  """.update.run.transact(xa)

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

    assertIO(results, List(Right(()), Right(())))
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
