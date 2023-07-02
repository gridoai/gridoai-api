package com.gridoai.utils

extension [E, T](e: Either[E, T])
  def addLocationToLeft(implicit
      line: sourcecode.Line,
      file: sourcecode.File
  ) =
    e.left.map(x => s"${file.value}:${line.value} $x")
