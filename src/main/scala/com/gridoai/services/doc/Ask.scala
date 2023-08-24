package com.gridoai.services.doc

import cats.Monad
import cats.effect.IO
import cats.implicits.*
import com.gridoai.adapters.llm.*
import com.gridoai.domain.*
import com.gridoai.models.DocDB
import com.gridoai.utils.*
import com.gridoai.endpoints.*

import com.gridoai.adapters.*
import com.gridoai.auth.AuthData

def ask(auth: AuthData)(payload: AskPayload)(implicit
    db: DocDB[IO]
): IO[Either[String, AskResponse]] = traceMappable("ask"):
  payload.messages.last.from match
    case MessageFrom.Bot =>
      IO.pure(Left("Last message should be from the user"))
    case MessageFrom.User =>
      askRecursively(auth)(
        payload.messages,
        payload.basedOnDocsOnly
      )

def askRecursively(auth: AuthData)(
    messages: List[Message],
    basedOnDocsOnly: Boolean,
    lastQuery: Option[String] = None,
    lastChunks: List[Chunk] = List.empty,
    searchesBeforeResponse: Int = 2
)(implicit
    db: DocDB[IO]
): IO[Either[String, AskResponse]] =
  val llmModel = LLMModel.Gpt35Turbo
  val llm = getLLM(llmModel)
  println("Used llm: " + llm.toString())

  if searchesBeforeResponse > 0 then
    llm
      .chooseAction(messages, lastQuery, lastChunks)
      .flatMapRight:
        case Action.Ask =>
          println("AI decided to ask...")
          llm
            .ask(lastChunks, basedOnDocsOnly, messages, lastQuery.isDefined)
            .mapRight: question =>
              AskResponse(
                message = question,
                sources = lastChunks.map(_.documentName).distinct
              )
        case Action.Answer =>
          println("AI decided to answer...")
          llm
            .answer(lastChunks, basedOnDocsOnly, messages, lastQuery.isDefined)
            .mapRight: answer =>
              AskResponse(
                message = answer,
                sources = lastChunks.map(_.documentName).distinct
              )
        case Action.Search =>
          println("AI decided to search...")
          llm
            .buildQueryToSearchDocuments(messages, lastQuery, lastChunks)
            .flatMapRight: newQuery =>
              println(s"AI's query: $newQuery")
              searchDoc(auth)(
                newQuery,
                llm.maxTokensForChunks(messages, basedOnDocsOnly),
                llmModel |> llmToStr
              )
                .flatMapRight: newChunks =>
                  askRecursively(auth)(
                    messages,
                    basedOnDocsOnly,
                    Some(newQuery),
                    newChunks,
                    searchesBeforeResponse - 1
                  )
  else
    llm
      .ask(lastChunks, basedOnDocsOnly, messages, lastQuery.isDefined)
      .mapRight: answer =>
        AskResponse(
          message = answer,
          sources = lastChunks.map(_.documentName).distinct
        )
