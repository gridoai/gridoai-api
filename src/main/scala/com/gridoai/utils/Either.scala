package com.gridoai.utils
import cats.implicits.*
import cats.Monad

extension [E, T](e: Either[E, T])
  def addLocationToLeft(implicit
      line: sourcecode.Line,
      file: sourcecode.File
  ) =
    e.left.map(x => s"${file.value}:${line.value} $x")

def fallbackEitherM[I, O, E, F[_]: Monad](
    f1: I => F[Either[E, O]],
    f2: I => F[Either[E, O]]
)(i: I) =
  f1(i).flatMap:
    case Left(_) => f2(i)
    case x       => x.pure[F]
