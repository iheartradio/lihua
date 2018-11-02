package lihua

import cats.tagless.{autoFunctorK, finalAlg, autoContravariant}

import scala.concurrent.duration.FiniteDuration
/**
 * Final tagless encoding of the DAO Algebra
 * @tparam F effect Monad
 * @tparam T type of the domain model
 */
@autoFunctorK(autoDerivation = true) @finalAlg @autoContravariant
trait EntityDAO[F[_], T, Query] {

  def idQuery(id: ObjectId): Query

  def get(id: ObjectId): F[Entity[T]]

  def insert(t: T): F[Entity[T]]

  def update(entity: Entity[T]): F[Entity[T]]

  def upsert(entity: Entity[T]): F[Entity[T]]

  def invalidateCache(query: Query): F[Unit]

  def find(query: Query): F[Vector[Entity[T]]]

  def findOne(query: Query): F[Entity[T]]

  def findOneOption(query: Query): F[Option[Entity[T]]]

  def findCached(query: Query, ttl: FiniteDuration): F[Vector[Entity[T]]]

  def remove(id: ObjectId): F[Unit]

  def removeAll(query: Query): F[Int]

  def update(query: Query, entity: Entity[T], upsert: Boolean): F[Entity[T]]
}


