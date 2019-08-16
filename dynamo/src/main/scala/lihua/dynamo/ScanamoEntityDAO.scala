package lihua
package dynamo

import cats.effect.Async
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import org.scanamo._
import org.scanamo.syntax._
import org.scanamo.auto._
import cats.implicits._
import ScanamoEntityDAO._
import lihua.EntityDAO.EntityDAOMonad
import org.scanamo.error.DynamoReadError
import org.scanamo.ops.ScanamoOps

import scala.util.Random

class ScanamoEntityDAO[F[_], A: DynamoFormat]
  (tableName: String,
   client: AmazonDynamoDBAsync)
  (implicit F: Async[F])
  extends EntityDAOMonad[F, A, List[EntityId]]{

  private val table = Table[Entity[A]](tableName)
  private val sc = ScanamoCats[F](client)

  private def exec[T](ops: ScanamoOps[Option[Either[DynamoReadError, T]]]): F[T] =
    sc.exec(ops)
      .flatMap(_.liftTo[F](MissingResultScanamoError)
      .flatMap(_.leftMap(ScanamoError(_)).liftTo[F]))

  private def execVoid[T](ops: ScanamoOps[Option[Either[DynamoReadError, T]]]): F[Unit] =
    sc.exec(ops)
      .flatMap(_.fold(F.unit)(_.leftMap(ScanamoError(_)).liftTo[F].void))

  private def execSet[T](ops: ScanamoOps[Set[Either[DynamoReadError, T]]]): F[Vector[T]] =
    sc.exec(ops)
      .flatMap(_.toVector.traverse(_.leftMap(ScanamoError(_)).liftTo[F]))

  def get(id: EntityId): F[Entity[A]] = exec(table.get(idFieldName -> id.value))

  def insert(t: A): F[Entity[A]] =
    F.delay(Random.alphanumeric.take(20).mkString).flatMap { id =>
      exec(table.put(t.toEntity(id)))
    }

  private def toUniqueKeys(q: List[EntityId]) = idFieldName -> q.toSet.asInstanceOf[Set[String]]

  def update(entity: Entity[A]): F[Entity[A]] = upsert(entity)

  def upsert(entity: Entity[A]): F[Entity[A]] = execVoid(table.put(entity)).as(entity)

  def find(query: List[EntityId]): F[Vector[Entity[A]]] =
    execSet(table.getAll(toUniqueKeys(query)))

  def findOne(query: List[EntityId]): F[Entity[A]] = findOneOption(query).flatMap(_.liftTo[F](UnexpectedNumberOfResult))

  def findOneOption(query: List[EntityId]): F[Option[Entity[A]]] = find(query).map(_.headOption)

  def remove(id: EntityId): F[Unit] = sc.exec(table.delete(idFieldName -> id)).void

  def removeAll(query: List[EntityId]): F[Int] = sc.exec(table.deleteAll(toUniqueKeys(query))).map(_.size)

  def removeAll(): F[Int] = {
    sc.exec(
      for {
        all <- table.scan()
        ids = all.flatMap(_.toOption.map(_._id))
        r <- table.deleteAll(toUniqueKeys(ids))
      } yield r.size)
  }
}


object ScanamoEntityDAO {
  implicit val entityIdDynamoFormat: DynamoFormat[EntityId] =
    DynamoFormat[String].asInstanceOf[DynamoFormat[EntityId]]

  case object MissingResultScanamoError extends RuntimeException
  case object UnexpectedNumberOfResult extends RuntimeException
  case class ScanamoError(se: org.scanamo.error.ScanamoError) extends RuntimeException
}