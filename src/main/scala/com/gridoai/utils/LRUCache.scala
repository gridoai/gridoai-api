package com.gridoai.utils

import scala.collection.mutable
import cats.effect.IO

class LRUCache[K, V](val capacity: Int):
  private val cache = mutable.LinkedHashMap.empty[K, V]

  private def evict(): Unit =
    cache.remove(cache.head._1)

  def get(key: K): Option[V] = synchronized:
    cache
      .remove(key)
      .map: value =>
        cache.put(key, value)
        value

  def put(key: K, value: V): Unit = synchronized:
    cache.remove(key)
    if (cache.size >= capacity) evict()
    cache.put(key, value)

def useCacheToIgnore[K, V, E](lruCache: LRUCache[K, Unit], key: K)(
    io: IO[Either[E, Unit]]
): IO[Either[E, Unit]] = synchronized:
  lruCache.get(key) match
    case None =>
      lruCache.put(key, ())
      io
    case Some(_) => IO.pure(Right(()))
