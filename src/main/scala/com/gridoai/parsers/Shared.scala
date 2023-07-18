package com.gridoai.parsers

def filterNonUtf8(text: String) =
  text.replaceAll("[\\x00]|[^\\x01-\\x7F]", "")
