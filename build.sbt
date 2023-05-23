val scala3Version = "3.2.2"

lazy val root = project
  .in(file("."))
  .settings(
    name := "API",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    scalacOptions += "-Ypartial-unification",
    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test,
    libraryDependencies += "com.google.cloud.functions" % "functions-framework-api" % "1.0.4" % "provided",
    libraryDependencies += "org.neo4j.driver" % "neo4j-java-driver" % "5.8.0",
    libraryDependencies += "org.typelevel" %% "cats-core" % "2.9.0"
  )

lazy val app = (project in file("app"))
  .settings(
    assembly / mainClass := Some("com.programandonocosmos.ScalaHttpFunction")
  )
