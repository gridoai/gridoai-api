package com.gridoai.utils

import cats.effect.IO
import cats.syntax.functor._
import cats.syntax.applicativeError._

extension [T](a: T)
  def trace =
    println(a)
    a

extension [T](a: IO[T])
  def trace(msg: String = ""): IO[T] =
    a.attempt.flatTap(attempt => IO(println(s"$msg: $attempt"))).rethrow
