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
import cats.effect.{IO, Sync}
import cats.implicits.*
import java.util.Date
import scala.util.Try
import scala.jdk.CollectionConverters.SeqHasAsJava
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest
import concurrent.duration.DurationInt

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
    Sync[IO].blocking(
      flow
        .newTokenRequest(code)
        .setRedirectUri(redirectUri)
        .execute()
        .asRight
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
        flowAndCodeToTokens(flow, code, redirectUri).map(_.flatMap: t =>
          println("Tokens got!")
          Option(t.getRefreshToken) match
            case Some(refreshToken) =>
              Right((t.getAccessToken, refreshToken))
            case None => Left("Missing refresh token")
        )

  def refreshToken(
      refreshToken: String
  ): IO[Either[String, (String, String)]] =
    Sync[IO].blocking(
      Right:
        GoogleRefreshTokenRequest(
          NetHttpTransport(),
          GsonFactory.getDefaultInstance,
          refreshToken,
          CLIENT_ID,
          CLIENT_SECRET
        ).execute.getAccessToken -> refreshToken
    ) |> attempt

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
