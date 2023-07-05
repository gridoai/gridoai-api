package com.gridoai.adapters.llm

import cats.effect.IO

trait LLM[F[_]]:
  def ask(context: String)(prompt: String): F[Either[String, String]]

def getLLMByName(name: String): LLM[IO] =
  name match
    case "palm2"  => Paml2Client
    case "mocked" => MockLLM[IO]

def getLLM(name: String): LLM[IO] =
  sys.env.get("USE_MOCKED_LLM") match
    case Some("1") => MockLLM[IO]
    case _         => getLLMByName(name)
