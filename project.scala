//> using scala 3

//> using option "-Xmax-inlines:80"

//> using publish.organization "com.gridoai"
//> using publish.name "gridoai-api"
//> using publish.version "0.1.0-SNAPSHOT"

//> using packaging.packageType "assembly"

// Attempt to compile to graal vm native image
//> using packaging.graalvmArgs "--initialize-at-build-time=ch.qos.logback.classic.Level"
//> using packaging.graalvmArgs "--initialize-at-build-time=ch.qos.logback.classic.Logger"
//> using packaging.graalvmArgs "--initialize-at-build-time=ch.qos.logback.core.util.Loader"
//> using packaging.graalvmArgs "--initialize-at-build-time=ch.qos.logback.core.CoreConstants"
//> using packaging.graalvmArgs "--initialize-at-build-time=ch.qos.logback.core.status.InfoStatus"
//> using packaging.graalvmArgs "--initialize-at-build-time=ch.qos.logback.core.util.StatusPrinter"

//> using packaging.graalvmArgs "--initialize-at-build-time=org.slf4j.LoggerFactory"

// Java libraries

//> using dep "com.stripe:stripe-java:24.5.0"

//> using dep "ch.qos.logback:logback-classic:1.4.11"
//> using dep "com.github.loki4j:loki-logback-appender:1.4.2"

//> using dep "org.apache.poi:poi:5.2.4"
//> using dep "org.apache.poi:poi-ooxml:5.2.4"
//> using dep "org.apache.pdfbox:pdfbox:2.0.30"

//> using dep "com.pgvector:pgvector:0.1.3"
//> using dep "com.knuddels:jtokkit:0.6.1"
// Google stuff
//> using dep "com.google.auth:google-auth-library-oauth2-http:1.20.0"
//> using dep "com.google.api-client:google-api-client:2.2.0"
//> using dep "com.google.oauth-client:google-oauth-client-jetty:1.34.1"
//> using dep "com.google.apis:google-api-services-drive:v3-rev20230822-2.0.0"

// Scala libraries
//> using dep "org.scala-lang.modules::scala-java8-compat:1.0.2"
//> using dep "org.typelevel::cats-core::2.10.0"
//> using dep "org.typelevel::cats-effect:3.5.2"
//> using test.dep "org.typelevel::munit-cats-effect:2.0.0-M1"
//> using dep "io.circe::circe-core:0.14.6"
//> using dep "io.circe::circe-generic:0.14.6"
//> using dep "io.circe::circe-parser:0.14.6"

//> using dep "org.http4s::http4s-ember-server:0.23.24"
//> using dep "org.http4s::http4s-circe:0.23.24"
//> using dep "org.http4s::http4s-dsl:0.23.24"
//> using dep "org.http4s::http4s-ember-server:0.23.24"

//> using dep "com.softwaremill.sttp.client3::cats:3.9.1"
//> using dep "com.softwaremill.sttp.tapir::tapir-http4s-server:1.9.2"
//> using dep "com.softwaremill.sttp.tapir::tapir-core:1.9.2"
//> using dep "com.softwaremill.sttp.tapir::tapir-json-circe:1.9.2"
//> using dep "com.softwaremill.sttp.tapir::tapir-openapi-docs:1.9.2"
//> using dep "com.softwaremill.sttp.apispec::openapi-circe-yaml:0.7.3"
//> using dep "com.softwaremill.sttp.tapir::tapir-vertx-server-cats:1.9.2"
//> using dep "com.softwaremill.sttp.tapir::tapir-http4s-server:1.9.2"
//> using dep "com.softwaremill.sttp.tapir::tapir-json-circe:1.9.2"
//> using test.dep "com.softwaremill.sttp.tapir::tapir-sttp-stub-server:1.9.2"
//> using test.dep "com.softwaremill.sttp.client3::circe:3.9.1"

//> using dep "de.killaitis::http4s-cloud-functions:0.4.3"

//> using dep "com.github.jwt-scala::jwt-core:9.4.5"
//> using dep "com.lihaoyi::sourcecode:0.3.1"

//> using dep "org.tpolecat::doobie-core:1.0.0-RC4"
//> using dep "org.tpolecat::doobie-hikari:1.0.0-RC4"
//> using dep "com.zaxxer:HikariCP:5.1.0"
//> using dep "org.tpolecat::doobie-postgres:1.0.0-RC4"

//> using dep "dev.maxmelnyk::openai-scala:0.3.0"
//> using dep "com.github.tototoshi::scala-csv:1.3.10"

//> using test.dep "org.scalatest::scalatest:3.2.17"

//> using test.dep "org.scalameta::munit:0.7.29"
//> using resourceDir "src/main/resources"
//> using dep io.ably:ably-java:1.2.33
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:1.9.6

//> using dep "dev.profunktor:redis4cats-effects_3:1.5.2"
//> using dep "com.resend:resend-java:2.2.1"
