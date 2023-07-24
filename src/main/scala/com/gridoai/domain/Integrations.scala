package com.gridoai.domain

case class gdriveImportPayload(
    clientId: String,
    clientSecret: String,
    code: String,
    paths: List[String]
)
