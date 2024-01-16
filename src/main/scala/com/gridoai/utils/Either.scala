package com.gridoai.utils
import cats.implicits.*
import cats.Monad
import cats.data.EitherT

extension [E, T](e: Either[E, T])
  def addLocationToLeft(implicit
      line: sourcecode.Line,
      file: sourcecode.File
  ) =
    e.left.map(x => s"${file.value}:${line.value} $x")

def fallbackEitherM[I, O, E, F[_]: Monad](
    f1: I => EitherT[F, E, O],
    f2: I => EitherT[F, E, O]
)(i: I) =
  (f1(i).value
    .flatMap:
      case Left(e) =>
        println(s"First function call failed, trying fallback. Error: $e")
        f2(i).value
      case x => x.pure[F]
    )
    .asEitherT
