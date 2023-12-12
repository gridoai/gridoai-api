package com.gridoai.endpoints

import com.gridoai.models.DocDB
import com.gridoai.services.doc.*
import com.gridoai.services.doc.GDrive.*
import cats.effect.IO
import sttp.tapir.server.ServerEndpoint
import com.gridoai.services.payments.createBillingSession
import com.gridoai.adapters.stripe
object withService:

  def searchDocs(implicit db: DocDB[IO]) =
    searchEndpoint.serverLogic(searchDoc _)

  def webHooksStripeEndpoint =
    webhooksStripe.serverLogic(
      stripe.handleEvent _
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
    authGDriveEndpoint.serverLogic(GDrive.auth _)

  def importGDriveDocs(implicit db: DocDB[IO]) =
    importGDriveEndpoint.serverLogic(GDrive.importDocs)

  def askLLM(implicit db: DocDB[IO]) =
    askEndpoint.serverLogic(ask _)

  def refreshGDriveToken =
    refreshGDriveTokenEndpoint.serverLogic(GDrive.refreshToken _)

  def billingSessionEndpoint = billingSession.serverLogic(createBillingSession)

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
      webHooksStripeEndpoint,
      billingSessionEndpoint,
      refreshGDriveToken
    )
