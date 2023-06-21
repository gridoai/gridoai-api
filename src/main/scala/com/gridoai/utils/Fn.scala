package com.gridoai.utils
import cats.effect.IO

extension [A](a: A) {
  inline def |>[B](inline f: A => B): B = f(a)
}

def attempt[T](x: IO[Either[String, T]]): IO[Either[String, T]] =
  x.attempt.map(_.flatten.left.map(_.toString()))
