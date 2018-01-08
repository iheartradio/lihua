/*
* Copyright [2017] [iHeartMedia Inc]
* All rights reserved
*/
package lihua
package mongo

import cats.data.{EitherT, NonEmptyList}
import lihua.mongo.IOEntityDAO._
import lihua.mongo.IOEntityDAO.DBError._

import play.api.libs.json.{Format, JsObject}
import reactivemongo.play.json.collection.JSONCollection
import reactivemongo.play.json._
import cats.implicits._

import scala.concurrent.{Future, ExecutionContext => EC}
import Result._
import cats.effect.IO
import lihua.mongo.EntityDAO.Query
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.api.Cursor.ErrorHandler
import reactivemongo.api.commands.WriteResult

import scala.concurrent.duration.FiniteDuration
import scalacache._
import modes.scalaFuture._
import caffeine._

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

  sealed trait DBError extends Product with Serializable

  object DBError {

    case object NotFound extends DBError

    case class DBException(throwable: Throwable) extends DBError

    case class WriteError(details: NonEmptyList[WriteErrorDetail]) extends DBError

    sealed trait WriteErrorDetail extends Product with Serializable {
      def code: Int
      def msg: String
    }

    case class ItemWriteErrorDetail(code: Int, msg: String) extends WriteErrorDetail
    case class WriteConcernErrorDetail(code: Int, msg: String) extends WriteErrorDetail

  }

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
}
