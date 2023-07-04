package com.gridoai.utils
import cats.effect.IO
import cats.Functor
import cats.implicits.toFunctorOps
import cats.ApplicativeError

extension [A](a: A) {
  inline def |>[B](inline f: A => B): B = f(a)
}

extension [E, T](x: IO[Either[E, T]])
  def mapRight[V](f: T => V): IO[Either[E, V]] =
    x.map(_.map(f))

  def mapLeft[V](f: E => V): IO[Either[V, T]] =
    x.map(_.left.map(f))

  def flatMapRight[V](f: T => IO[Either[E, V]]): IO[Either[E, V]] =
    x.mapRight(f) |> flattenIOEitherIOEither

def attempt[T](x: IO[Either[String, T]]): IO[Either[String, T]] =
  x.attempt.map(_.flatten.left.map(_.toString().trace).addLocationToLeft)

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

/** Transforms a list of `Either[E, Unit]` into an `Either[List[E], Unit]`.
  *
  * This function processes a list of `Either` values. If any of them are
  * `Left`, it collects all `Left` values into a list and returns a `Left`
  * containing that list. If there are no `Left` values, it returns a `Right`
  * containing `Unit`.
  *
  * @param list
  *   The list of `Either[E, Unit]` to be processed.
  * @tparam E
  *   The type of the value in the `Left` part of the `Either`.
  * @return
  *   An `Either[List[E], Unit]`. If the input list contains any `Left` values,
  *   the returned `Either` is a `Left` containing a list of all `Left` values.
  *   If the input list does not contain any `Left` values, the returned
  *   `Either` is a `Right` containing `Unit`.
  */
def collectLeftsOrElseUnit[E](
    list: List[Either[E, ?]]
): Either[List[E], Unit] = {
  val lefts = list.collect { case Left(e) => e }
  if (lefts.isEmpty) Right(())
  else Left(lefts)
}
