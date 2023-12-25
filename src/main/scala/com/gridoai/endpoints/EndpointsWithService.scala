package com.gridoai.endpoints

import com.gridoai.models.DocDB
import com.gridoai.services.doc.*
import com.gridoai.services.doc.GDrive.*
import cats.effect.IO
import sttp.tapir.server.ServerEndpoint
import com.gridoai.services.payments.createBillingSession
import com.gridoai.adapters.stripe
import com.gridoai.services.notifications.createNotificationServiceToken
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import com.gridoai.adapters.notifications.NotificationService

class withService(implicit db: DocDB[IO], ns: NotificationService[IO]):

  def searchDocs =
    searchEndpoint.serverLogic(searchDoc _)

  def webHooksStripeEndpoint =
    webhooksStripe.serverLogic(
      stripe.handleEvent _
    )

  def healthCheck =
    healthCheckEndpoint.serverLogic(_ => IO.pure(Right("OK")))

  def createDocument =
    createDocumentEndpoint.serverLogic(createDoc _)

  def uploadDocs =
    fileUploadEndpoint.serverLogic(uploadDocuments _)

  def deleteDoc =
    deleteEndpoint.serverLogic(deleteDocument _)

  def authNotification =
    notificationAuthEndpoint.serverLogic(authData =>
      _ => createNotificationServiceToken(authData)
    )

  def listDocs =
    listEndpoint.serverLogic(listDocuments _)

  def authGDrive =
    authGDriveEndpoint.serverLogic(GDrive.auth _)

  def importGDriveDocs =
    importGDriveEndpoint.serverLogic(GDrive.importDocs)

  def askLLM =
    askEndpoint.serverLogic(ask _)

  def refreshGDriveToken =
    refreshGDriveTokenEndpoint.serverLogic(GDrive.refreshToken _)

  def billingSessionEndpoint = billingSession.serverLogic(createBillingSession)

  def apiEndpoints: List[ServerEndpoint[Any, IO]] =
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
      refreshGDriveToken,
      authNotification
    )
  def docEndpoints: List[ServerEndpoint[Any, IO]] =
    SwaggerInterpreter()
      .fromServerEndpoints[IO](apiEndpoints, "gridoai-api", "1.0.0")
  def allEndpoints: List[ServerEndpoint[Any, IO]] =
    docEndpoints ++ apiEndpoints
