package com.gridoai.services.doc

import cats.effect.IO
import cats.implicits._
import com.gridoai.adapters.llm._
import com.gridoai.domain._
import com.gridoai.models.DocDB
import com.gridoai.utils._

import com.gridoai.adapters._
import com.gridoai.auth.AuthData
import org.slf4j.LoggerFactory

import com.gridoai.adapters.notifications.NotificationService

def ask(auth: AuthData)(payload: AskPayload)(implicit
    db: DocDB[IO],
    ns: NotificationService[IO]
): IO[Either[String, AskResponse]] =
  val llmModel = LLMModel.Gpt35Turbo
  val llm = getLLM(llmModel)
  val logger = LoggerFactory.getLogger(getClass.getName)

  logger.info(s"messages: ${payload.messages}")
  logger.info(s"llm: ${llm.toString}")
  logger.info(s"basedOnDocsOnly: ${payload.basedOnDocsOnly}")
  logger.info(s"useActions: ${payload.useActions}")
  logger.info(s"scope: ${payload.scope}")

  def askRecursively(
      lastQueries: List[String],
      searchesBeforeResponse: Int
  )(
      lastChunks: List[Chunk]
  ): IO[Either[String, AskResponse]] =
    chooseAction(
      lastQueries,
      lastChunks,
      searchesBeforeResponse
    )
      !> runAction(lastQueries, lastChunks, searchesBeforeResponse)

  def chooseAction(
      lastQueries: List[String],
      lastChunks: List[Chunk],
      searchesBeforeResponse: Int
  ): IO[Either[String, Action]] =
    if payload.useActions then
      val options =
        if searchesBeforeResponse > 0 then
          List(Action.Search, Action.Answer, Action.Ask)
        else List(Action.Answer, Action.Ask)

      llm.chooseAction(
        payload.messages,
        lastQueries,
        lastChunks,
        options
      )
    else
      IO(searchesBeforeResponse match
        case 2 => Action.Search.asRight
        case 1 => Action.Answer.asRight
        case _ =>
          Left(
            s"Invalid state. searchesBeforeResponse = $searchesBeforeResponse and payload.useActions = $payload.useActions"
          )
      )

  def runAction(
      lastQueries: List[String],
      lastChunks: List[Chunk],
      searchesBeforeResponse: Int
  )(action: Action): IO[Either[String, AskResponse]] =
    (action match
      case Action.Ask    => doAskAction
      case Action.Answer => doAnswerAction
      case Action.Search => doSearchAction
    )(lastQueries, lastChunks, searchesBeforeResponse)

  def doAskAction(
      lastQueries: List[String],
      lastChunks: List[Chunk],
      searchesBeforeResponse: Int
  ): IO[Either[String, AskResponse]] =
    logger.info("AI decided to ask...")
    llm
      .ask(
        lastChunks,
        payload.basedOnDocsOnly,
        payload.messages,
        !lastQueries.isEmpty
      )
      .mapRight: question =>
        AskResponse(
          message = question,
          sources = lastChunks.map(_.documentName).distinct
        )

  def doAnswerAction(
      lastQueries: List[String],
      lastChunks: List[Chunk],
      searchesBeforeResponse: Int
  ): IO[Either[String, AskResponse]] =
    logger.info("AI decided to answer...")
    llm
      .answer(
        lastChunks,
        payload.basedOnDocsOnly,
        payload.messages,
        !lastQueries.isEmpty
      )
      .mapRight: answer =>
        AskResponse(
          message = answer,
          sources = lastChunks.map(_.documentName).distinct
        )

  def doSearchAction(
      lastQueries: List[String],
      lastChunks: List[Chunk],
      searchesBeforeResponse: Int
  ): IO[Either[String, AskResponse]] =
    logger.info("AI decided to search...")
    llm
      .buildQueriesToSearchDocuments(payload.messages, lastQueries, lastChunks)
      .flatMapRight: newQueries =>
        logger.info(s"AI's queries: $newQueries")
        searchDoc(auth)(
          SearchPayload(
            queries = newQueries,
            tokenLimit = llm
              .maxTokensForChunks(
                payload.messages,
                payload.basedOnDocsOnly
              ),
            llmName = llmModel |> llmToStr,
            scope = payload.scope
          )
        ) !> askRecursively(
          newQueries,
          searchesBeforeResponse - 1
        )

  traceMappable("ask"):
    payload.messages.last.from match
      case MessageFrom.Bot =>
        IO.pure(Left("Last message should be from the user"))
      case MessageFrom.User =>
        doSearchAction(List.empty, List.empty, 2)
