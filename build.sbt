import sbt._
import sbtassembly.MergeStrategy

import scala.sys.process._

val http4sVersion = "0.23.19"
val scala3Version = "3.3.0"
val circeVersion = "0.14.1"

val deploy = Command.command("deploy") { (state: State) =>
  "sbt assembly".!
  "gcloud functions deploy api --region=us-west1 --entry-point=com.gridoai.ScalaHttpFunction --runtime=java17 --trigger-http --allow-unauthenticated --memory=512MB --source=target/scala-3.3.0/".!
  "rm target/scala-3.3.0/*-SNAPSHOT.jar".!
  state
}

lazy val root = project
  .in(file("."))
  .settings(
    name := "API",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    commands += deploy,
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
    libraryDependencies += "de.killaitis" %% "http4s-cloud-functions" % "0.4.3",
    libraryDependencies += "org.typelevel" %% "munit-cats-effect" % "2.0.0-M1" % "test",
    libraryDependencies += "org.apache.pdfbox" % "pdfbox" % "2.0.28",
    libraryDependencies += "com.google.auth" % "google-auth-library-oauth2-http" % "1.3.0"
  )
  .enablePlugins(GraalVMNativeImagePlugin)

libraryDependencies ++= Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % "1.5.0",
  "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.4.0"
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
lazy val app = (project in file("app"))
  .settings(
    assembly / mainClass := Some("com.gridoai.ScalaHttpFunction")
  )

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "io.netty.versions.properties") =>
    MergeStrategy.discard
  case "module-info.class" =>
    MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
