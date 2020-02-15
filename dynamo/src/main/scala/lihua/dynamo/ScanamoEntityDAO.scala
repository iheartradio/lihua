package lihua
package dynamo

import cats.effect.Async
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync
import org.scanamo._
import org.scanamo.syntax._
import org.scanamo.generic.auto._
import cats.implicits._
import ScanamoEntityDAO._
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.dynamodbv2.model.{
  AttributeDefinition,
  CreateTableRequest,
  CreateTableResult,
  DescribeTableRequest,
  DescribeTableResult,
  KeySchemaElement,
  KeyType,
  ProvisionedThroughput,
  ResourceNotFoundException,
  ScalarAttributeType
}
import org.scanamo.ops.ScanamoOps
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._

import scala.util.Random
import io.estatico.newtype.ops._

abstract class ScanamoDAOHelper[F[_], A: DynamoFormat](
    tableName: String,
    client: AmazonDynamoDBAsync
  )(implicit F: Async[F]) {
  protected val table = Table[A](tableName)

  protected val sc = ScanamoCats[F](client)

  protected def execTraversableOnce[TO[_], T](
      ops: ScanamoOps[TO[Either[DynamoReadError, T]]]
    )(implicit ev: TO[Either[DynamoReadError, T]] <:< TraversableOnce[
        Either[DynamoReadError, T]
      ]
    ): F[Vector[T]] =
    sc.exec(ops)
      .flatMap(
        t => ev(t).toVector.traverse(_.leftMap(ScanamoError(_)).liftTo[F])
      )
}

class ScanamoEntityDAO[F[_], A: DynamoFormat](
    tableName: String,
    client: AmazonDynamoDBAsync
  )(implicit F: Async[F])
    extends ScanamoDAOHelper[F, Entity[A]](tableName, client)
    with EntityDAO[F, A, List[EntityId]] {
  def get(id: EntityId): F[Entity[A]] =
    sc.exec(table.get(idFieldName -> id.value))
      .flatMap(
        _.liftTo[F](EntityNotFound(id))
          .flatMap(_.leftMap(ScanamoError(_)).liftTo[F])
      )

  def insert(t: A): F[Entity[A]] =
    F.delay(Random.alphanumeric.take(20).mkString).flatMap { id =>
      val e = t.toEntity(id)
      sc.exec(table.put(e)).as(e)
    }

  private def toUniqueKeys(q: List[EntityId]) =
    idFieldName -> q.toSet.coerce[Set[String]]

  def update(entity: Entity[A]): F[Entity[A]] = upsert(entity)

  def upsert(entity: Entity[A]): F[Entity[A]] =
    sc.exec(table.put(entity)).as(entity)

  def find(query: List[EntityId]): F[Vector[Entity[A]]] =
    execTraversableOnce(table.getAll(toUniqueKeys(query)))

  def findOne(query: List[EntityId]): F[Entity[A]] =
    findOneOption(query).flatMap(_.liftTo[F](UnexpectedNumberOfResult))

  def findOneOption(query: List[EntityId]): F[Option[Entity[A]]] =
    find(query).map(_.headOption)

  def remove(id: EntityId): F[Unit] =
    sc.exec(table.delete(idFieldName -> id)).void

  def removeAll(query: List[EntityId]): F[Int] =
    sc.exec(table.deleteAll(toUniqueKeys(query))).as(query.size)

  def removeAll(): F[Int] = {
    sc.exec(for {
      all <- table.scan()
      ids = all.flatMap(_.toOption.map(_._id))
      r <- table.deleteAll(toUniqueKeys(ids))
    } yield ids.size)
  }

  def all: F[Vector[Entity[A]]] =
    execTraversableOnce(table.scan())

  /**
    * update the first entity query finds
    *
    * @param query  search query
    * @param entity to be updated to
    * @param upsert whether to insert of nothing is found
    * @return whether anything is updated
    */
  def update(
      query: List[EntityId],
      entity: Entity[A],
      upsert: Boolean
    ): F[Boolean] =
    if (upsert) update(entity).as(true)
    else
      find(query)
        .flatMap(_.traverse(e => update(e.copy(data = entity.data))))
        .map(_.nonEmpty)

  def upsert(
      query: List[EntityId],
      t: A
    ): F[Entity[A]] =
    findOneOption(query).flatMap(
      _.fold(insert(t))(e => update(e.copy(data = t)))
    )
}

trait ScanamoManagement {
  import scala.collection.JavaConverters._
  private def attributeDefinitions(
      attributes: Seq[(String, ScalarAttributeType)]
    ) =
    attributes.map {
      case (symbol, attributeType) =>
        new AttributeDefinition(symbol, attributeType)
    }.asJava

  private def keySchema(attributes: Seq[(String, ScalarAttributeType)]) = {
    val hashKeyWithType :: rangeKeyWithType = attributes.toList
    val keySchemas = hashKeyWithType._1 -> KeyType.HASH :: rangeKeyWithType
      .map(_._1 -> KeyType.RANGE)
    keySchemas.map {
      case (symbol, keyType) => new KeySchemaElement(symbol, keyType)
    }.asJava
  }

  private def asyncHandle[
      F[_],
      Req <: com.amazonaws.AmazonWebServiceRequest,
      Resp
    ](f: AsyncHandler[Req, Resp] => java.util.concurrent.Future[Resp]
    )(implicit F: Async[F]
    ): F[Resp] =
    F.async { (cb: Either[Throwable, Resp] => Unit) =>
      val handler = new AsyncHandler[Req, Resp] {
        def onError(exception: Exception): Unit =
          cb(Left(exception))

        def onSuccess(
            req: Req,
            result: Resp
          ): Unit =
          cb(Right(result))
      }

      f(handler)
      ()
    }

  def createTable[F[_]](
      client: AmazonDynamoDBAsync,
      tableName: String,
      keyAttributes: Seq[(String, ScalarAttributeType)],
      readCapacityUnits: Long,
      writeCapacityUnits: Long
    )(implicit F: Async[F]
    ): F[Unit] = {
    val req = new CreateTableRequest(tableName, keySchema(keyAttributes))
      .withAttributeDefinitions(attributeDefinitions(keyAttributes))
      .withProvisionedThroughput(
        new ProvisionedThroughput(readCapacityUnits, writeCapacityUnits)
      )

    asyncHandle[F, CreateTableRequest, CreateTableResult](
      client.createTableAsync(req, _)
    ).void
  }

  def ensureTable[F[_]](
      client: AmazonDynamoDBAsync,
      tableName: String,
      keyAttributes: Seq[(String, ScalarAttributeType)],
      readCapacityUnits: Long,
      writeCapacityUnits: Long
    )(implicit F: Async[F]
    ): F[Unit] = {
    asyncHandle[F, DescribeTableRequest, DescribeTableResult](
      client.describeTableAsync(new DescribeTableRequest(tableName), _)
    ).void.recoverWith {
      case e: ResourceNotFoundException =>
        createTable(
          client,
          tableName,
          keyAttributes,
          readCapacityUnits,
          writeCapacityUnits
        )
    }
  }
}

object ScanamoEntityDAO extends ScanamoManagement {
  implicit val entityIdDynamoFormat: DynamoFormat[EntityId] =
    DynamoFormat[String].asInstanceOf[DynamoFormat[EntityId]]

  case class EntityNotFound(id: EntityId)
      extends RuntimeException(s"Entity of id $id is not found")

  case object UnexpectedNumberOfResult extends RuntimeException
  case class ScanamoError(se: org.scanamo.ScanamoError)
      extends RuntimeException(se.toString)

  val entityIdKeyAttributes = Seq(lihua.idFieldName -> S)

  def ensureTable[F[_]](
      client: AmazonDynamoDBAsync,
      tableName: String,
      readCapacityUnits: Long = 10L,
      writeCapacityUnits: Long = 10L
    )(implicit F: Async[F]
    ): F[Unit] =
    ensureTable[F](
      client,
      tableName,
      entityIdKeyAttributes,
      readCapacityUnits,
      writeCapacityUnits
    )
}
