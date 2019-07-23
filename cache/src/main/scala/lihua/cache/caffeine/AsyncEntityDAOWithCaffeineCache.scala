package lihua.cache.caffeine

import cats.Functor
import cats.implicits._
import lihua.cache.EntityCache
import lihua.{Entity, EntityDAO}
import scalacache.caffeine._
import scalacache.{Cache, Mode, cachingF}

import scala.concurrent.duration.Duration

object implicits {
  implicit def toAsyncWithCaffeineCache[T,  F[_]: Mode: Functor, Q](dao: EntityDAO[F, T, Q]): EntityCache[T, F, Q] =
    new AsyncEntityDAOWithCaffeineCache(dao)
}


class AsyncEntityDAOWithCaffeineCache[T,  F[_]: Mode: Functor, Q](private val dao: EntityDAO[F, T, Q]) extends EntityCache[T, F, Q] {

  implicit val scalaCache: Cache[Vector[Entity[T]]] = CaffeineCache[Vector[Entity[T]]]

  def invalidateCache(q: Q): F[Unit] =
    scalacache.remove(q).void

  def findCached(query: Q, ttl: Duration): F[Vector[Entity[T]]] =
    if (ttl.length == 0L)
      dao.find(query)
    else
      cachingF(query)(Some(ttl)) {
        dao.find(query)
      }
}