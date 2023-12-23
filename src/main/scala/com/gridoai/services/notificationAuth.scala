package com.gridoai.services

import com.gridoai.auth.AuthData
import com.gridoai.adapters.notifications.generateToken
import cats.effect.IO

def createNotificationServiceToken(authData: AuthData) =
  generateToken[IO](authData.userId)
