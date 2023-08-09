package com.gridoai.adapters

import com.gridoai.utils.*
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.AccessToken
import com.google.api.services.drive.Drive
import com.google.api.client.googleapis.auth.oauth2.{
  GoogleAuthorizationCodeFlow,
  GoogleClientSecrets
}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse
import cats.effect.IO
import cats.implicits.*
import java.util.Date
import scala.util.Try
import scala.jdk.CollectionConverters.SeqHasAsJava
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest
import java.util.Arrays

val CLIENT_ID =
  sys.env.getOrElse("GOOGLE_CLIENT_ID", "")
val CLIENT_SECRET =
  sys.env.getOrElse("GOOGLE_CLIENT_SECRET", "")

object GoogleClient:

  private def flowAndCodeToTokens(
      flow: GoogleAuthorizationCodeFlow,
      code: String,
      redirectUri: String
  ): IO[Either[String, GoogleTokenResponse]] =
    (IO:
      Right:
        flow
          .newTokenRequest(code)
          .setRedirectUri(redirectUri)
          .execute()
    ) |> attempt

  private def buildGoogleAuthorizationCodeFlow(
      scopes: List[String]
  ): Either[String, GoogleAuthorizationCodeFlow] =
    Try:
      val httpTransport = NetHttpTransport()
      val jsonFactory = GsonFactory.getDefaultInstance()
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
    .toEither.left
      .map: e =>
        s"Google authorization code flow failed to build: $e"

  def exchangeCodeForTokens(
      code: String,
      redirectUri: String,
      scopes: List[String]
  ): IO[Either[String, (String, String)]] =

    println("Building google authorization code flow...")

    buildGoogleAuthorizationCodeFlow(scopes)
      .pure[IO]
      .flatMapRight: flow =>
        println("Google authorization code flow builded.")
        println("Sending request to get token...")
        flowAndCodeToTokens(flow, code, redirectUri).mapRight(t =>
          (t.getAccessToken, t.getRefreshToken)
        )

  def refreshToken(
      refreshToken: String
  ): IO[Either[String, (String, String)]] =
    (IO:
      Right:
        val token = GoogleRefreshTokenRequest(
          NetHttpTransport(),
          GsonFactory.getDefaultInstance(),
          refreshToken,
          CLIENT_ID,
          CLIENT_SECRET
        ).execute()
        (token.getAccessToken, refreshToken)
    ) |> attempt

  def buildDriveService(token: String): Drive =
    val expiryTime =
      Date(System.currentTimeMillis() + 3600 * 1000) // 1 hour later
    val accessToken = AccessToken(token, expiryTime)
    val credentials = GoogleCredentials.create(accessToken)
    val jsonFactory = GsonFactory.getDefaultInstance()
    val httpTransport = NetHttpTransport()

    Drive
      .Builder(
        httpTransport,
        jsonFactory,
        HttpCredentialsAdapter(credentials)
      )
      .setApplicationName("GridoAI")
      .build()
