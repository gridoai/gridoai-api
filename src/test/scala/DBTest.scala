import cats.effect.IO
import munit.CatsEffectSuite
import doobie.implicits._
import com.gridoai.domain._
import com.gridoai.utils._

import java.util.UUID

class DocumentServiceTest extends CatsEffectSuite {
  // Create a test transactor
  import com.gridoai.models.{xa, PostgresClient}

  // Create a test schema if it doesn't exist
  val createSchema =
    sql"CREATE SCHEMA IF NOT EXISTS test".update.run.transact(xa)

  // Create a test table if it doesn't exist
  val createTable = sql"""
    CREATE TABLE IF NOT EXISTS test.documents (
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

  val doc = DocumentWithEmbedding(
    Document(
      UUID.randomUUID(), // Fill in with appropriate data
      "name",
      "source",
      "The sky is blue",
      10
    ), // Fill in with appropriate data

    List.range(1, 769).map(_.toFloat)
    // Fill in with appropriate data
  ) // Fill in with appropriate data
  test("addDocument should insert a document into the database") {
    // Arrange

    // Act
    val result = PostgresClient.addDocument(doc, "org1", "role1")

    // Assert
    assertIO(result, Right(()))
  }

  test("getNearDocuments should return documents near the given embedding") {
    // Arrange
    val embedding = doc.embedding // Fill in with appropriate data

    // Act
    val result =
      PostgresClient.getNearDocuments(embedding, 10, "org1", "role1")

    // Assert
    assertIO(
      result.map(_.isRight),
      true
    ) // Check that the result is a Right value
    assertIO(
      result.map(_.getOrElse(List.empty).length),
      1
    ) // Check that the result contains the document we inserted
  }

}
