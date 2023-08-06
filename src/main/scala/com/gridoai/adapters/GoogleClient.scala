package com.gridoai.adapters

import com.gridoai.utils.*

import com.google.api.client.googleapis.auth.oauth2.{
  GoogleAuthorizationCodeFlow,
  GoogleClientSecrets
}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import scala.util.Try
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse
import scala.jdk.CollectionConverters.SeqHasAsJava

val CLIENT_ID =
  sys.env.getOrElse("GOOGLE_CLIENT_ID", "")
val CLIENT_SECRET =
  sys.env.getOrElse("GOOGLE_CLIENT_SECRET", "")

object GoogleClient:

  private def flowAndCodeToTokens(
      flow: GoogleAuthorizationCodeFlow,
      code: String,
      redirectUri: String
  ): Either[String, GoogleTokenResponse] =
    Try:
      flow
        .newTokenRequest(code)
        .setRedirectUri(redirectUri)
        .execute()
    .toEither.left
      .map(e => s"Failed to request token from code: $e")

  def exchangeCodeForTokens(
      code: String,
      redirectUri: String,
      scopes: List[String]
  ): Either[String, (String, String)] =
    val httpTransport = new NetHttpTransport()
    val jsonFactory = GsonFactory.getDefaultInstance()
    val secrets = new GoogleClientSecrets.Details()
      .setClientId(CLIENT_ID)
      .setClientSecret(CLIENT_SECRET)
    val clientSecrets = new GoogleClientSecrets().setWeb(secrets)

    println("Building google authorization code flow...")

    Try:
      new GoogleAuthorizationCodeFlow.Builder(
        httpTransport,
        jsonFactory,
        clientSecrets,
        scopes.asJava
      )
        .setAccessType("offline") // Enables refresh tokens
        .build()
    .toEither
      .flatMap: flow =>
        println("Google authorization code flow builded.")
        println("Sending request to get token...")
        flowAndCodeToTokens(flow, code, redirectUri).map(t =>
          (t.getAccessToken, t.getRefreshToken)
        )
      .left
      .map: e =>
        s"Google authorization code flow failed to build: $e"
