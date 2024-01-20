package com.gridoai.adapters

import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.AccessToken
import com.google.api.services.drive.Drive
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse
import cats.effect.IO
import cats.effect.Sync
import cats.implicits._
import cats.data.EitherT
import java.util.Date
import scala.util.Try
import scala.jdk.CollectionConverters.SeqHasAsJava
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest
import concurrent.duration.DurationInt
import org.slf4j.LoggerFactory

import com.gridoai.utils._

val CLIENT_ID =
  sys.env.getOrElse("GOOGLE_CLIENT_ID", "")
val CLIENT_SECRET =
  sys.env.getOrElse("GOOGLE_CLIENT_SECRET", "")

object GoogleClient:
  val logger = LoggerFactory.getLogger(getClass.getName)

  private def flowAndCodeToTokens(
      flow: GoogleAuthorizationCodeFlow,
      code: String,
      redirectUri: String
  ): EitherT[IO, String, GoogleTokenResponse] =
    Sync[IO]
      .blocking(
        flow
          .newTokenRequest(code)
          .setRedirectUri(redirectUri)
          .execute()
          .asRight
      )
      .asEitherT
      .attempt

  private def buildGoogleAuthorizationCodeFlow(
      scopes: List[String]
  ): Either[String, GoogleAuthorizationCodeFlow] =
    try
      val httpTransport = NetHttpTransport()
      val jsonFactory = GsonFactory.getDefaultInstance
      val secrets = GoogleClientSecrets
        .Details()
        .setClientId(CLIENT_ID)
        .setClientSecret(CLIENT_SECRET)
      val clientSecrets = GoogleClientSecrets().setWeb(secrets)
      GoogleAuthorizationCodeFlow
        .Builder(
          httpTransport,
          jsonFactory,
          clientSecrets,
          scopes.asJava
        )
        .setAccessType("offline") // Enables refresh tokens
        .build()
        .asRight
    catch
      case e: java.lang.Exception =>
        s"Google authorization code flow failed to build: $e".asLeft

  def exchangeCodeForTokens(
      code: String,
      redirectUri: String,
      scopes: List[String]
  ): EitherT[IO, String, (String, String)] =

    logger.info("Building google authorization code flow...")

    buildGoogleAuthorizationCodeFlow(scopes)
      .pure[IO]
      .asEitherT
      .flatMap: flow =>
        logger.info("Google authorization code flow builded.")
        logger.info("Sending request to get token...")
        flowAndCodeToTokens(flow, code, redirectUri).subflatMap: t =>
          logger.info("Tokens got!")
          Option(t.getRefreshToken) match
            case Some(refreshToken) =>
              Right((t.getAccessToken, refreshToken))
            case None => Left("Missing refresh token")

  def refreshToken(
      refreshToken: String
  ): EitherT[IO, String, (String, String)] =
    Sync[IO]
      .blocking(
        Right:
          GoogleRefreshTokenRequest(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance,
            refreshToken,
            CLIENT_ID,
            CLIENT_SECRET
          ).execute.getAccessToken -> refreshToken
      )
      .asEitherT
      .attempt

  def buildDriveService(token: String): Drive =
    val expiryTime =
      Date(System.currentTimeMillis + 1.hour.toMillis)
    val accessToken = AccessToken(token, expiryTime)
    val credentials = GoogleCredentials.create(accessToken)

    Drive
      .Builder(
        NetHttpTransport(),
        GsonFactory.getDefaultInstance,
        HttpCredentialsAdapter(credentials)
      )
      .setApplicationName("GridoAI")
      .build()
