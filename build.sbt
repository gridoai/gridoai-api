val scala3Version = "3.2.2"

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
  )

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

lazy val app = (project in file("app"))
  .settings(
    assembly / mainClass := Some("com.programandonocosmos.ScalaHttpFunction")
  )
