import com.gridoai.domain.*
import com.gridoai.models.Queries

import java.util.UUID
// For more information on writing tests, see
// https://scalameta.org/munit/docs/getting-started.html
class CypherQueryTest extends munit.FunSuite:
  test("addDocument generates correct Cypher") {
    val doc = Document(
      UUID.fromString("24c625cf-c907-4521-84d4-3ffcb200e66d"),
      "Name2",
      "Very cool content here2",
      "s3://path/to/file2.md",
      4
    )
    val cypher = Queries.addDocument(doc)
    assertEquals(
      cypher,
      "CREATE (:`Document` {uid: '24c625cf-c907-4521-84d4-3ffcb200e66d', name: 'Name2', content: 'Very cool content here2', url: 's3://path/to/file2.md', numberOfWords: 4})"
    )
  }

  test("addMentions generates correct Cypher") {
    val mentions = Mentions(
      1234,
      UUID.fromString("fbb6cf8c-1ce4-41fe-afb6-f9914174f46d"),
      UUID.fromString("08234d78-ee39-43ab-8d4a-fced62344391")
    )
    val cypher = Queries.addMentions(mentions)
    assertEquals(
      cypher,
      "MATCH (from:`Document`) WHERE from.uid = 'fbb6cf8c-1ce4-41fe-afb6-f9914174f46d' MATCH (to:`Document`) WHERE to.uid = '08234d78-ee39-43ab-8d4a-fced62344391' CREATE (from)-[:`MENTIONS`]->(to)"
    )
  }
  test("getDocumentsByIds generates correct Cypher") {
    val ids = List(
      UUID.fromString("24c625cf-c907-4521-84d4-3ffcb200e66d"),
      UUID.fromString("fbb6cf8c-1ce4-41fe-afb6-f9914174f46d")
    )
    val cypher = Queries.getDocumentsByIds(ids)
    assertEquals(
      cypher,
      "MATCH (doc:`Document`) WHERE doc.uid IN ['24c625cf-c907-4521-84d4-3ffcb200e66d', 'fbb6cf8c-1ce4-41fe-afb6-f9914174f46d'] RETURN doc"
    )
  }

  test("getDocumentById generates correct Cypher") {
    val id = UUID.fromString("24c625cf-c907-4521-84d4-3ffcb200e66d")
    val cypher = Queries.getDocumentById(id)
    assertEquals(
      cypher,
      "MATCH (doc:`Document`) WHERE doc.uid = '24c625cf-c907-4521-84d4-3ffcb200e66d' RETURN doc"
    )
  }

  test("getDocumentMentions generates correct Cypher") {
    val id = UUID.fromString("24c625cf-c907-4521-84d4-3ffcb200e66d")
    val cypher = Queries.getDocumentMentions(id)
    assertEquals(
      cypher,
      "MATCH (from:`Document` {uid: '24c625cf-c907-4521-84d4-3ffcb200e66d'})-[:`MENTIONS`]->(to:`Document`) RETURN to"
    )
  }
