package com.programandonocosmos.adapters

import cats.effect._
import org.neo4j.driver._

import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

// TODO: Map n4j exceptions to our own error types
type Neo4jQueryRunner = String => IO[List[Record]]

def makeQueryRunner(driver: Driver)(implicit ec: ExecutionContext) =
  (query: String) =>
    IO.async_[List[Record]] { callback =>
      driver
        .asyncSession()
        .runAsync(query)
        .thenCompose(
          _.listAsync()
            .thenAccept(records => callback(Right(records.asScala.toList)))
        )
    }

object Neo4jAsync:
  def resource(url: String, user: String, password: String)(implicit
      ec: ExecutionContext
  ): Resource[IO, Neo4jQueryRunner] =
    val acquire = IO {
      GraphDatabase.driver(url, AuthTokens.basic(user, password))
    }
    val release = (driver: Driver) =>
      IO.fromFuture(IO(driver.closeAsync().toScala.map(_ => ())))
    Resource.make(acquire)(release).map(makeQueryRunner(_))
