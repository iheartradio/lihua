/*
* Copyright [2017] [iHeartMedia Inc]
* All rights reserved
*/
package lihua
package mongo

import cats.data.{EitherT, NonEmptyList}
import lihua.mongo.IOEntityDAO._
import lihua.mongo.DBError._
import play.api.libs.json.{Format, JsObject}
import reactivemongo.play.json.collection.JSONCollection
import reactivemongo.play.json._
import cats.implicits._

import scala.concurrent.{Future, ExecutionContext => EC}
import Result._
import cats.effect.{Effect, IO}
import EntityDAO.Query
import cats.{ ~>}
import mainecoon.FunctorK
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.api.Cursor.ErrorHandler
import reactivemongo.api.commands.WriteResult

import scala.concurrent.duration.FiniteDuration
import scalacache.Cache
import scalacache.cachingF
import scalacache.modes.scalaFuture._
import scalacache.caffeine._
import scala.util.control.NoStackTrace

class IOEntityDAO[T: Format](collection: JSONCollection)(implicit ex: EC) extends EntityDAO[Result, T] {
  implicit val scalaCache: Cache[Vector[Entity[T]]] = CaffeineCache[Vector[Entity[T]]]

  lazy val writeCollection = collection.withReadPreference(ReadPreference.primary)

  def get(id: ObjectId): Result[Entity[T]] = of(
    collection.find(Query.idSelector(id)).one[Entity[T]]
  )

  def find(q: Query): Result[Vector[Entity[T]]] = of {
    internalFind(q)
  }

  def invalidateCache(q: Query): Result[Unit] =
    of(scalacache.remove(q)).as(())

  private def internalFind(q: Query): Future[Vector[Entity[T]]] = {
    var builder = collection.find(q.selector)
    builder = q.hint.fold(builder)(builder.hint)
    builder = q.opts.fold(builder)(builder.options)
    builder = q.sort.fold(builder)(builder.sort)
    builder.cursor[Entity[T]](q.readPreference.getOrElse(collection.readPreference)).
      collect[Vector](-1, errorHandler)
  }

  def findCached(query: Query, ttl: FiniteDuration): Result[Vector[Entity[T]]] = of {
    if (ttl.length == 0L)
      internalFind(query)
    else
      cachingF(query)(Some(ttl)) {
        internalFind(query)
      }
  }

  def insert(t: T): Result[Entity[T]] = of {
    val entity = Entity(ObjectId.generate, t)
    writeCollection.insert(entity).map(parseWriteResult(_).as(entity))
  }

  def remove(id: ObjectId): Result[Unit] =
    removeAll(Query.idSelector(id)).ensure(NotFound)(_ > 0).void

  def update(entity: Entity[T]): Result[Entity[T]] = of {
    writeCollection.update(Query.idSelector(entity._id), entity)
  }.as(entity)

  def removeAll(selector: JsObject): Result[Int] = of {
    writeCollection.remove(selector)
  }

  private val errorHandler: ErrorHandler[Vector[Entity[T]]] = Cursor.FailOnError()

}

object IOEntityDAO {

  type Result[T] = EitherT[IO, DBError, T]

  object Result {
    private type FE[T] = Future[Either[DBError, T]]

    implicit def fromFutureOption[T](f: Future[Option[T]])(implicit ec: EC): FE[T] =
      f.map(_.toRight(NotFound))

    implicit def fromFuture[T](f: Future[T])(implicit ec: EC): FE[T] =
      f.map(_.asRight[DBError])

    implicit def fromFutureWriteResult(f: Future[WriteResult])(implicit ec: EC): FE[Int] =
      f.map(parseWriteResult(_))

    def parseWriteResult(wr: WriteResult): Either[DBError, Int] = {
      val errs: List[WriteErrorDetail] = wr.writeErrors.toList.map(e => ItemWriteErrorDetail(e.code, e.errmsg)) ++
        wr.writeConcernError.toList.map(e => WriteConcernErrorDetail(e.code, e.errmsg))
      NonEmptyList.fromList(errs).map(WriteError).toLeft(wr.n)
    }

    def of[T](f: => FE[T])(implicit ec: EC): Result[T] =
      EitherT(IO.fromFuture(IO(f.recover {
        case e: Throwable => DBException(e).asLeft[T]
      })))

  }

  class MongoError(e: DBError) extends Exception with NoStackTrace

  /**
   * creates a DAO that use IO for error handling directly
   */
  def direct[F[_], A: Format](daoR: IOEntityDAO[A])(implicit ec: EC, F: Effect[F]): EntityDAO[F, A] = {
    type DBResult[T] = EitherT[IO, DBError, T]
    val fk = implicitly[FunctorK[EntityDAO[?[_], A]]]
    fk.mapK(daoR)(λ[DBResult ~> F] { dr =>
      F.liftIO(dr.value).flatMap(F.fromEither)
    })

  }
}
