package lihua.cache

import cats.effect.Async
import lihua.{Entity, EntityDAO, EntityDAOWithCache}
import scalacache.Cache
import scalacache.cachingF
import scalacache.caffeine._
import scalacache.CatsEffect.modes._
import cats.implicits._

import scala.concurrent.duration.Duration

class AsyncEntityDAOWithCache[T,  F[_]: Async, Q](val dao: EntityDAO[F, T, Q]) extends EntityDAOWithCache[F, T, Q] {

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
