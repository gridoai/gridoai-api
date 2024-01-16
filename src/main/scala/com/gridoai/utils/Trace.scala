package com.gridoai.utils

import cats.effect.IO
import cats.data.EitherT

extension [T](a: T)
  def trace =
    println(a)
    a

  def traceFn(f: T => String) =
    println(f(a))
    a

extension [T](a: IO[T])
  def trace(msg: String = ""): IO[T] =
    a.attempt.flatTap(attempt => IO.println(s"$msg: $attempt")).rethrow

  def trace(f: T => String): IO[T] =
    a.attempt.flatTap(attempt => IO.println(attempt.map(f))).rethrow

extension [T, E](a: EitherT[IO, E, T])
  def traceRight(f: T => String): EitherT[IO, E, T] =
    a.value.attempt
      .flatTap(attempt => IO.println(attempt.map(_.map(f))))
      .rethrow
      .asEitherT

  def traceLeft(f: E => String): EitherT[IO, E, T] =
    a.value.attempt
      .flatTap(attempt => IO.println(attempt.map(_.left.map(f))))
      .rethrow
      .asEitherT
