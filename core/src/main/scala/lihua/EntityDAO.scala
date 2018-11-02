package lihua

import cats.Contravariant
import cats.tagless.{autoFunctorK, finalAlg}

import scala.concurrent.duration.Duration
/**
 * Final tagless encoding of the DAO Algebra
 * @tparam F effect Monad
 * @tparam T type of the domain model
 */
@autoFunctorK(autoDerivation = true) @finalAlg
trait EntityDAO[F[_], T, Query] {

  def get(id: ObjectId): F[Entity[T]]

  def insert(t: T): F[Entity[T]]

  def update(entity: Entity[T]): F[Entity[T]]

  def upsert(entity: Entity[T]): F[Entity[T]]

  def invalidateCache(query: Query): F[Unit]

  def find(query: Query): F[Vector[Entity[T]]]

  def findOne(query: Query): F[Entity[T]]

  def findOneOption(query: Query): F[Option[Entity[T]]]

  def findCached(query: Query, ttl: Duration): F[Vector[Entity[T]]]

  def remove(id: ObjectId): F[Unit]

  def removeAll(query: Query): F[Int]

  def update(query: Query, entity: Entity[T], upsert: Boolean): F[Entity[T]]
}

object EntityDAO {
  implicit def contravriantEntityDAO[F[_], T]: Contravariant[EntityDAO[F, T, ?]] = new Contravariant[EntityDAO[F, T, ?]] {
    def contramap[A, B](ea: EntityDAO[F, T, A])(f: B => A): EntityDAO[F, T, B] = new EntityDAO[F, T, B] {
      def get(id: ObjectId): F[Entity[T]] = ea.get(id)

      def insert(t: T): F[Entity[T]] = ea.insert(t)

      def update(entity: Entity[T]): F[Entity[T]] = ea.update(entity)

      def upsert(entity: Entity[T]): F[Entity[T]] = ea.upsert(entity)

      def invalidateCache(query: B): F[Unit] =
        ea.invalidateCache(f(query))

      def find(query: B): F[Vector[Entity[T]]] =
        ea.find(f(query))

      def findOne(query: B): F[Entity[T]] =
        ea.findOne(f(query))

      def findOneOption(query: B): F[Option[Entity[T]]] =
        ea.findOneOption(f(query))

      def findCached(query: B, ttl: Duration): F[Vector[Entity[T]]] =
        ea.findCached(f(query), ttl)

      def remove(id: ObjectId): F[Unit] = ea.remove(id)

      def removeAll(query: B): F[Int] = ea.removeAll(f(query))

      def update(query: B, entity: Entity[T], upsert: Boolean): F[Entity[T]] =
        ea.update(f(query), entity, upsert)
    }
  }
}
