package com.programandonocosmos.adapters

import cats.effect.*
import org.neo4j.driver.*
import org.neo4j.driver.async.AsyncSession
import org.neo4j.driver.types.Entity

import scala.compat.java8.FutureConverters.*
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.*

val NEO4J_URI = sys.env.getOrElse("NEO4J_URI", "bolt://localhost:7687")
val NEO4J_USER = sys.env.getOrElse("NEO4J_USER", "neo4j")
val NEO4J_PASSWORD = sys.env.getOrElse("NEO4J_PASSWORD", "password")
// TODO: Map n4j exceptions to our own error types
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import org.neo4j.driver.summary.ResultSummary

type Neo4jQueryRunner = String => IO[(ResultSummary, List[Record])]

def makeQueryRunner(driver: Driver)(implicit ec: ExecutionContext) =
  (query: String) =>
    IO.fromFuture(IO {
      val session = driver.session(classOf[AsyncSession])
      for
        stmtResult <- session.runAsync(query).toScala
        records <- stmtResult
          .listAsync()
          .toScala
          .map(_.asScala.toList)
        summary <- stmtResult.consumeAsync().toScala
      yield (summary, records)
    })

object Neo4jAsync:
  def resource(url: String, user: String, password: String)(implicit
      ec: ExecutionContext
  ): Resource[IO, Neo4jQueryRunner] =
    println(s"Connecting to $url with user $user...")
    val acquire = IO {
      GraphDatabase.driver(url, AuthTokens.basic(user, password))
    }

    val release = (driver: Driver) =>
      IO.fromFuture(IO(driver.closeAsync().toScala.map(_ => ())))
    Resource.make(acquire)(release).map(makeQueryRunner(_))

  def resourceWithCredentials(implicit ec: ExecutionContext) =
    Neo4jAsync.resource(NEO4J_URI, NEO4J_USER, NEO4J_PASSWORD)
