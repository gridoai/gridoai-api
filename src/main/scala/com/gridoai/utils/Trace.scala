package com.gridoai.utils

import cats.effect.IO

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

extension [T, E](a: IO[Either[E, T]])
  def traceRight(f: T => String): IO[Either[E, T]] =
    a.attempt.flatTap(attempt => IO.println(attempt.map(_.map(f)))).rethrow

  def traceLeft(f: E => String): IO[Either[E, T]] =
    a.attempt.flatTap(attempt => IO.println(attempt.map(_.left.map(f)))).rethrow
