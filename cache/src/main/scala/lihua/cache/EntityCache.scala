package lihua
package cache

import scala.concurrent.duration.Duration


trait EntityCache[T,  F[_], Q] {
  def invalidateCache(q: Q): F[Unit]
  def findCached(query: Q, ttl: Duration): F[Vector[Entity[T]]]

}
