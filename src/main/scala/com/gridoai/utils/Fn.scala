package com.gridoai.utils
import cats.effect.IO
import cats.Functor
import cats.implicits.toFunctorOps
import cats.ApplicativeError
import cats.implicits._
import cats.effect.implicits._
import cats.data.EitherT

import cats.Monad
extension [A](a: A) {
  inline def |>[B](inline f: A => B): B = f(a)
}

extension [E, T, F[_]: Monad](x: F[Either[E, T]])
  def mapRight[V](f: T => V) =
    x.map(_.map(f))

  def mapLeft[V](f: E => V) =
    x.map(_.left.map(f))

  def flatMapRight[V](f: T => F[Either[E, V]]) =
    x.map(_.fold(Left(_).pure, f)).flatten

  def flatMapLeft[V](f: E => F[Either[V, T]]) =
    x.map(_.fold(f, Right(_).pure)).flatten

  def !>[V](f: T => F[Either[E, V]]): F[Either[E, V]] = flatMapRight[V](f)

  def asEitherT = EitherT(x)

def attempt[T, F[_], E <: Either[Any, T]](
    x: F[E]
)(using
    ae: ApplicativeError[F, Throwable],
    line: sourcecode.Line,
    file: sourcecode.File
): F[Either[String, T]] =
  ae.attempt(x).map(_.flatten.left.map(_.toString().trace).addLocationToLeft)

extension [E, T, F[_]](x: EitherT[F, E, T])
  def attempt(using
      ae: ApplicativeError[F, Throwable],
      line: sourcecode.Line,
      file: sourcecode.File
  ): EitherT[F, String, T] = EitherT:
    ae.attempt(x.value)
      .map(_.flatten.left.map(_.toString().trace).addLocationToLeft)

  def !>[V](f: T => EitherT[F, E, V])(implicit F: Monad[F]): EitherT[F, E, V] =
    x.flatMap(f)

  def flatMapEither[V](f: T => Either[E, V])(implicit
      F: Monad[F]
  ): EitherT[F, E, V] =
    x.value.map(_.flatMap(f)).asEitherT

  def mapEither[V](f: Either[E, T] => Either[E, V])(implicit
      F: Monad[F]
  ): EitherT[F, E, V] =
    x.value.map(f).asEitherT

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

/** Logs the time taken to process a value in a functor.
  *
  * This function takes a functor `F[T]` and a label as arguments. It applies a
  * side-effectful operation to the value inside the functor, logging the time
  * taken for the computation and the label for identification.
  *
  * If an exception is thrown during the computation, it catches the error, logs
  * it along with the time taken until the error occurred, and rethrows the
  * error wrapped in the `ApplicativeError`.
  *
  * @param label
  *   A string identifier that will be included in the log output.
  * @param x
  *   The functor `F[T]` containing the value to be processed.
  * @param ae
  *   An implicit `ApplicativeError[F, Throwable]` instance. This allows the
  *   function to handle errors in the functor `F[T]` and encapsulate them in
  *   the `ApplicativeError`.
  * @tparam T
  *   The type of the value in the functor.
  * @tparam F
  *   The type of the functor. Must have an instance of `Functor[F]` available
  *   implicitly.
  * @return
  *   The functor `F[T]` with the logged time of processing. If an error
  *   occurred, the error is logged and rethrown encapsulated in the
  *   `ApplicativeError`.
  */
def traceMappable[T, F[_]: Functor](label: String)(
    x: F[T]
)(implicit ae: ApplicativeError[F, Throwable]): F[T] =
  val now = System.currentTimeMillis()
  try
    x.map(y =>
      println(
        s"[$label] Time: ${System.currentTimeMillis() - now}ms"
      )
      y
    )
  catch
    case e: Throwable =>
      println(
        s"[$label] Time: ${System.currentTimeMillis() - now}ms Error: $e"
      )
      ae.raiseError(e)

/** Transforms a list of `Either[E, T]` into an `Either[List[E], List[T]]`.
  *
  * This function processes a list of `Either` values. If any of them are
  * `Left`, it collects all `Left` values into a list and returns a `Left`
  * containing that list. If there are no `Left` values, it collects all `Right`
  * values into a list and returns a `Right` containing that list.
  *
  * @param list
  *   The list of `Either[E, T]` to be processed.
  * @tparam E
  *   The type of the value in the `Left` part of the `Either`.
  * @tparam T
  *   The type of the value in the `Right` part of the `Either`.
  * @return
  *   An `Either[List[E], List[T]]`. If the input list contains any `Left`
  *   values, the returned `Either` is a `Left` containing a list of all `Left`
  *   values. If the input list does not contain any `Left` values, the returned
  *   `Either` is a `Right` containing a list of all `Right` values.
  */
def partitionEithers[E, T](list: List[Either[E, T]]): Either[List[E], List[T]] =
  val (lefts, rights) = list.partitionMap(identity)
  if (lefts.isEmpty) Right(rights) else Left(lefts)

extension [E, T, F[_]](x: List[EitherT[F, E, T]])
  def partitionEitherTs(implicit
      F: Monad[F]
  ): EitherT[F, List[E], List[T]] =
    x.traverse(_.value).map(partitionEithers).asEitherT

def executeByParts[T, E, V](
    f: List[T] => EitherT[IO, E, List[V]],
    partitionSize: Int
)(elements: List[T]): EitherT[IO, E, List[V]] =
  if elements.length <= partitionSize then f(elements)
  else
    for
      outputElements <- f(elements.slice(0, partitionSize))
      newOutputElements <- executeByParts(f, partitionSize)(
        elements.slice(partitionSize, elements.length)
      )
      _ = println(s"last batch size: ${outputElements.length}")
      _ = println(s"current batch size: ${newOutputElements.length}")
    yield outputElements ++ newOutputElements

def executeByPartsInParallel[T, E, V](
    f: List[T] => EitherT[IO, E, List[V]],
    partitionSize: Int,
    parallelismLevel: Int = 4
)(elements: List[T]): EitherT[IO, E, List[V]] =
  elements
    .grouped(partitionSize)
    .grouped(parallelismLevel)
    .toList
    .traverse(_.parTraverseN(parallelismLevel)(f(_).value))
    .map: results =>
      val (errors, successes) = results.flatten.separate
      errors.headOption match
        case Some(error) => (Left(error))
        case None        => (Right(successes.flatten))
    .asEitherT
