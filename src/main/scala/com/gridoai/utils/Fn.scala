package com.gridoai.utils
import cats.effect.IO

extension [A](a: A) {
  inline def |>[B](inline f: A => B): B = f(a)
}

def attempt[T](x: IO[Either[String, T]]): IO[Either[String, T]] =
  x.attempt.map(_.flatten.left.map(_.toString()))

def flattenIOEitherIOEither[E, T](
    x: IO[Either[E, IO[Either[E, T]]]]
): IO[Either[E, T]] =
  x.flatMap:
    case Left(error)    => IO.pure(Left(error))
    case Right(innerIO) => innerIO

def flattenIOEitherIO[E, T](
    x: IO[Either[E, IO[T]]]
): IO[Either[E, T]] =
  x.flatMap:
    case Left(error)    => IO.pure(Left(error))
    case Right(innerIO) => innerIO.map(y => Right(y))
