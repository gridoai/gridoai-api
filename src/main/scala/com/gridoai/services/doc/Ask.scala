package com.gridoai.services.doc

import cats.effect.IO
import cats.implicits.*
import com.gridoai.adapters.llm.*
import com.gridoai.domain.*
import com.gridoai.models.DocDB
import com.gridoai.utils.*

import com.gridoai.adapters.*
import com.gridoai.auth.AuthData

def ask(auth: AuthData)(payload: AskPayload)(implicit
    db: DocDB[IO]
): IO[Either[String, AskResponse]] =
  val llmModel = LLMModel.Gpt35Turbo
  val llm = getLLM(llmModel)
  val useActionsFeature = true
  println("Used llm: " + llm.toString())

  def askRecursively(
      lastQuery: Option[String],
      searchesBeforeResponse: Int
  )(
      lastChunks: List[Chunk]
  ): IO[Either[String, AskResponse]] =
    chooseAction(
      lastQuery,
      lastChunks,
      searchesBeforeResponse
    )
      !> runAction(lastQuery, lastChunks, searchesBeforeResponse)

  def chooseAction(
      lastQuery: Option[String],
      lastChunks: List[Chunk],
      searchesBeforeResponse: Int
  ): IO[Either[String, Action]] =
    if useActionsFeature then
      val options =
        if searchesBeforeResponse > 0 then
          List(Action.Search, Action.Answer, Action.Ask)
        else List(Action.Answer, Action.Ask)

      llm.chooseAction(
        payload.messages,
        lastQuery,
        lastChunks,
        options
      )
    else
      IO(searchesBeforeResponse match
        case 2 => Action.Search.asRight
        case 1 => Action.Answer.asRight
        case _ =>
          Left(
            s"Invalid state. searchesBeforeResponse = $searchesBeforeResponse and useActionsFeature = $useActionsFeature"
          )
      )

  def runAction(
      lastQuery: Option[String],
      lastChunks: List[Chunk],
      searchesBeforeResponse: Int
  )(action: Action): IO[Either[String, AskResponse]] =
    (action match
      case Action.Ask    => doAskAction
      case Action.Answer => doAnswerAction
      case Action.Search => doSearchAction
    )(lastQuery, lastChunks, searchesBeforeResponse)

  def doAskAction(
      lastQuery: Option[String],
      lastChunks: List[Chunk],
      searchesBeforeResponse: Int
  ): IO[Either[String, AskResponse]] =
    println("AI decided to ask...")
    llm
      .ask(
        lastChunks,
        payload.basedOnDocsOnly,
        payload.messages,
        lastQuery.isDefined
      )
      .mapRight: question =>
        AskResponse(
          message = question,
          sources = lastChunks.map(_.documentName).distinct
        )

  def doAnswerAction(
      lastQuery: Option[String],
      lastChunks: List[Chunk],
      searchesBeforeResponse: Int
  ): IO[Either[String, AskResponse]] =
    println("AI decided to answer...")
    llm
      .answer(
        lastChunks,
        payload.basedOnDocsOnly,
        payload.messages,
        lastQuery.isDefined
      )
      .mapRight: answer =>
        AskResponse(
          message = answer,
          sources = lastChunks.map(_.documentName).distinct
        )

  def doSearchAction(
      lastQuery: Option[String],
      lastChunks: List[Chunk],
      searchesBeforeResponse: Int
  ): IO[Either[String, AskResponse]] =
    println("AI decided to search...")
    llm
      .buildQueryToSearchDocuments(payload.messages, lastQuery, lastChunks)
      .flatMapRight: newQuery =>
        println(s"AI's query: $newQuery")
        searchDoc(auth)(
          SearchPayload(
            query = newQuery,
            tokenLimit =
              llm.maxTokensForChunks(payload.messages, payload.basedOnDocsOnly),
            llmName = llmModel |> llmToStr,
            scope = payload.scope
          )
        )
          .flatMapRight(
            askRecursively(
              Some(newQuery),
              searchesBeforeResponse - 1
            )
          )

  traceMappable("ask"):
    payload.messages.last.from match
      case MessageFrom.Bot =>
        IO.pure(Left("Last message should be from the user"))
      case MessageFrom.User =>
        askRecursively(None, 2)(List.empty)
