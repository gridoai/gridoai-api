package com.gridoai.endpoints

import com.gridoai.models.DocDB
import com.gridoai.services.doc.*
import cats.effect.IO
import sttp.tapir.server.ServerEndpoint

object withService:

  def searchDocs(implicit db: DocDB[IO]) =
    searchEndpoint.serverLogic(searchDoc _)

  def healthCheck =
    healthCheckEndpoint.serverLogic(_ => IO.pure(Right("OK")))

  def createDocument(implicit db: DocDB[IO]) =
    createDocumentEndpoint.serverLogic(createDoc _)

  def uploadDocs(implicit db: DocDB[IO]) =
    fileUploadEndpoint.serverLogic(uploadDocuments _)

  def askLLM(implicit db: DocDB[IO]) =
    askEndpoint.serverLogic(ask _)

  def allEndpoints(implicit db: DocDB[IO]): List[ServerEndpoint[Any, IO]] =
    List(searchDocs, healthCheck, createDocument, uploadDocs, askLLM)
