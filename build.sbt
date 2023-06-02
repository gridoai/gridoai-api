import sbtassembly.MergeStrategy
val http4sVersion = "0.23.19"
val scala3Version = "3.2.2"
val circeVersion = "0.14.1"
lazy val root = project
  .in(file("."))
  .settings(
    name := "API",
    version := "0.1.0-SNAPSHOT",
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
    libraryDependencies += "de.killaitis" %% "http4s-cloud-functions" % "0.4.3"
  )
enablePlugins(JavaServerAppPackaging)

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion
)
lazy val app = (project in file("app"))
  .settings(
    assembly / mainClass := Some("com.programandonocosmos.ScalaHttpFunction")
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
