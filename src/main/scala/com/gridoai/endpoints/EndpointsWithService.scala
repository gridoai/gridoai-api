package com.gridoai.endpoints

import com.gridoai.models.DocDB
import com.gridoai.services.doc.*
import cats.effect.IO
import sttp.tapir.server.ServerEndpoint

object withService:

  def searchDocs(implicit db: DocDB[IO]) =
    searchEndpoint.serverLogic(searchDoc _)

  def webHooksEndpoint =
    webhooks.serverLogic(
      com.gridoai.adapters.payments.handleCreateCostumer[IO] _
    )
  def healthCheck =
    healthCheckEndpoint.serverLogic(_ => IO.pure(Right("OK")))

  def createDocument(implicit db: DocDB[IO]) =
    createDocumentEndpoint.serverLogic(createDoc _)

  def uploadDocs(implicit db: DocDB[IO]) =
    fileUploadEndpoint.serverLogic(uploadDocuments _)

  def deleteDoc(implicit db: DocDB[IO]) =
    deleteEndpoint.serverLogic(deleteDocument _)

  def listDocs(implicit db: DocDB[IO]) =
    listEndpoint.serverLogic(listDocuments _)

  def authGDrive =
    authGDriveEndpoint.serverLogic(authenticateGDrive _)

  def importGDriveDocs(implicit db: DocDB[IO]) =
    importGDriveEndpoint.serverLogic(importGDriveDocuments _)

  def askLLM(implicit db: DocDB[IO]) =
    askEndpoint.serverLogic(ask _)

  def allEndpoints(implicit db: DocDB[IO]): List[ServerEndpoint[Any, IO]] =
    List(
      searchDocs,
      healthCheck,
      createDocument,
      uploadDocs,
      authGDrive,
      importGDriveDocs,
      askLLM,
      deleteDoc,
      listDocs,
      webHooksEndpoint
    )
