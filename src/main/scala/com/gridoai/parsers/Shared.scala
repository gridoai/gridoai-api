package com.gridoai.parsers

def filterNonUtf8(text: String) =
  text.replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}]", "")
