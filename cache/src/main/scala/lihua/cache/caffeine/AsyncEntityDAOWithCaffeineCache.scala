package lihua.cache.caffeine

import cats.Functor
import cats.effect.{Resource, Sync}
import cats.implicits._
import lihua.cache.EntityCache
import lihua.{Entity, EntityDAO}
import scalacache.caffeine._
import scalacache.{Cache, Mode, cachingF}

import scala.concurrent.duration.Duration

class AsyncEntityDAOWithCaffeineCache[T,  F[_]: Mode: Functor, Q] private (val dao: EntityDAO[F, T, Q])(implicit scalaCache: Cache[Vector[Entity[T]]]) extends EntityCache[T, F, Q] {

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


object AsyncEntityDAOWithCaffeineCache {
  def resource[T,  F[_]: Mode, Q](dao: EntityDAO[F, T, Q])(implicit F: Sync[F]): Resource[F, EntityCache[T, F, Q]] = {
    Resource.make(F.delay(CaffeineCache[Vector[Entity[T]]]))(
      _.close[F]().void
    ).map(implicit sc => new AsyncEntityDAOWithCaffeineCache(dao))
  }
}