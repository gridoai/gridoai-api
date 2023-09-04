import sbt._
import sbtassembly.MergeStrategy

import scala.sys.process._

val http4sVersion = "0.23.19"
val scala3Version = "3.3.0"
val circeVersion = "0.14.1"
val doobieVersion = "1.0.0-RC1"
val projectName = "API"
val currentVersion = "0.1.0-SNAPSHOT"

lazy val root = project
  .in(file("."))
  .settings(
    name := projectName,
    version := currentVersion,
    scalaVersion := scala3Version,
    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test,
    libraryDependencies += "com.google.cloud.functions" % "functions-framework-api" % "1.0.4" % "provided",
    libraryDependencies += "org.neo4j.driver" % "neo4j-java-driver" % "5.8.0",
    libraryDependencies += "org.neo4j" % "neo4j-cypher-dsl" % "2023.3.1",
    libraryDependencies += "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2",
    libraryDependencies += "org.typelevel" %% "cats-core" % "2.9.0",
    libraryDependencies += "org.typelevel" %% "cats-effect" % "3.5.0",
    libraryDependencies += "com.softwaremill.sttp.client3" %% "cats" % "3.8.15",
    libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % "1.5.0",
    libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-core" % "1.5.0",
    libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.5.0",
    libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % "1.5.5",
    libraryDependencies += "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.4.0",
    libraryDependencies += "de.killaitis" %% "http4s-cloud-functions" % "0.4.3",
    libraryDependencies += "org.typelevel" %% "munit-cats-effect" % "2.0.0-M1" % "test",
    libraryDependencies += "org.apache.pdfbox" % "pdfbox" % "2.0.28",
    libraryDependencies += "com.google.auth" % "google-auth-library-oauth2-http" % "1.3.0",
    libraryDependencies += "com.github.jwt-scala" %% "jwt-core" % "9.4.0",
    libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % "1.5.0" % Test,
    libraryDependencies += "com.lihaoyi" %% "sourcecode" % "0.3.0",
    libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-vertx-server-cats" % "1.6.0",
    libraryDependencies += ("org.scalamock" %% "scalamock" % "5.1.0" % Test)
      .cross(CrossVersion.for3Use2_13)
  )
  .enablePlugins(GraalVMNativeImagePlugin)

libraryDependencies ++= Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % "1.5.0",
  "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.4.0"
)
libraryDependencies ++= Seq(
  "org.apache.poi" % "poi" % "5.2.0",
  "org.apache.poi" % "poi-ooxml" % "5.2.0"
)
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion
)

libraryDependencies ++= Seq(
  "org.tpolecat" %% "doobie-core" % doobieVersion,
  "org.tpolecat" %% "doobie-postgres" % doobieVersion
)

libraryDependencies += "com.pgvector" % "pgvector" % "0.1.2"

libraryDependencies ++= Seq(
  "com.google.api-client" % "google-api-client" % "1.31.5",
  "com.google.oauth-client" % "google-oauth-client-jetty" % "1.31.5",
  "com.google.apis" % "google-api-services-drive" % "v3-rev20181213-1.28.0"
)

libraryDependencies += "dev.maxmelnyk" %% "openai-scala" % "0.3.0"

libraryDependencies += "com.knuddels" % "jtokkit" % "0.6.1"

libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "1.3.10"

lazy val app = (project in file("app"))
  .settings(
    assembly / mainClass := Some("com.gridoai.ScalaHttpFunction")
  )
scalacOptions ++= Seq(
  "-deprecation",
  "-Wvalue-discard",
  "-Wunused:all",
  "-Xmax-inlines:33"
)
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "io.netty.versions.properties") =>
    MergeStrategy.discard
  case PathList(ps @ _*) if ps.last == "module-info.class" =>
    MergeStrategy.concat
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
