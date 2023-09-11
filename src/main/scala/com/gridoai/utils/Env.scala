package com.gridoai.utils

import scala.io.Source
import scala.quoted.*

private val envFromFile: Map[String, String] =
  if sys.env.get("ENV").map(_.toLowerCase).contains("local") then
    val src = Source.fromFile(".env")
    val lines = src.getLines()
    val vars = lines.map: line =>
      val parts = line.split("=", 2).toList
      parts.headOption.getOrElse("") -> parts.lastOption.getOrElse("")
    vars.toMap
  else Map.empty

def requireEnvImpl(name: Expr[String])(using Quotes): Expr[String] = {
  import quotes.reflect.*
  val envVarName = name.valueOrAbort

  envFromFile
    .get(envVarName)
    .orElse(sys.env.get(envVarName))
    .getOrElse:
      report
        .error(
          s"Required env var $envVarName is not set"
        )

  '{
    sys.env.getOrElse(
      $name,
      throw new Exception(s"Required env var $$name is not set")
    )
  }
}

inline def getEnv(inline name: String) = ${ requireEnvImpl('name) }
