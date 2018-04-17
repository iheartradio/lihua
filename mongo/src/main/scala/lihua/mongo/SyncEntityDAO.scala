/*
* Copyright [2017] [iHeartMedia Inc]
* All rights reserved
*/
package lihua
package mongo

import cats.data.{EitherT, NonEmptyList}
import lihua.mongo.DBError._
import play.api.libs.json.{Format, JsObject}
import reactivemongo.play.json.collection.JSONCollection
import reactivemongo.play.json._
import cats.implicits._

import scala.concurrent.{Future, ExecutionContext => EC}
import cats.effect.{Async, IO, LiftIO}
import EntityDAO.{Query => Q}
import cats.{MonadError, ~>}
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


class SyncEntityDAO[T: Format, F[_]: Async](collection: JSONCollection)(implicit ex: EC)
  extends EntityDAO[SyncEntityDAO.Result[F, ?], T] {
  type R[A] = SyncEntityDAO.Result[F, A]
  import SyncEntityDAO.Result._

  implicit val scalaCache: Cache[Vector[Entity[T]]] = CaffeineCache[Vector[Entity[T]]]

  lazy val writeCollection = collection.withReadPreference(ReadPreference.primary) //due to a bug in ReactiveMongo

  def get(id: ObjectId): R[Entity[T]] = of(
    collection.find(Q.idSelector(id)).one[Entity[T]]
  )

  def find(q: Q): R[Vector[Entity[T]]] = of {
    internalFind(q)
  }

  def findOne(q: Q): R[Entity[T]] = of {
    builder(q).one[Entity[T]](readPref(q))
  }

  def findOneOption(q: Q): R[Option[Entity[T]]] = of {
    builder(q).one[Entity[T]](readPref(q))
  }

  def invalidateCache(q: Q): R[Unit] =
    of(scalacache.remove(q)).as(())

  private def internalFind(q: Q): Future[Vector[Entity[T]]] = {

    builder(q).cursor[Entity[T]](readPref(q)).
      collect[Vector](-1, errorHandler)
  }

  private def readPref(q: Q) = q.readPreference.getOrElse(collection.readPreference)

  private def builder(q: Q) = {
    var builder = collection.find(q.selector)
    builder = q.hint.fold(builder)(builder.hint)
    builder = q.opts.fold(builder)(builder.options)
    builder = q.sort.fold(builder)(builder.sort)
    builder
  }

  def findCached(query: Q, ttl: FiniteDuration): R[Vector[Entity[T]]] = of {
    if (ttl.length == 0L)
      internalFind(query)
    else
      cachingF(query)(Some(ttl)) {
        internalFind(query)
      }
  }

  def insert(t: T): R[Entity[T]] = of {
    val entity = Entity(ObjectId.generate, t)
    writeCollection.insert(entity).map(parseWriteResult(_).as(entity))
  }

  def upsert(entity: Entity[T]): R[Entity[T]] = of {
    writeCollection.update(Q.idSelector(entity._id), entity, upsert = true)
  }.as(entity)

  def remove(id: ObjectId): R[Unit] =
    removeAll(Q.idSelector(id)).ensure(NotFound)(_ > 0).void

  def update(entity: Entity[T]): R[Entity[T]] = of {
    writeCollection.update(Q.idSelector(entity._id), entity)
  }.as(entity)

  def removeAll(selector: JsObject): R[Int] = of {
    writeCollection.remove(selector)
  }


  private val errorHandler: ErrorHandler[Vector[Entity[T]]] = Cursor.FailOnError()

}

object SyncEntityDAO {
  type Result[F[_], T] = EitherT[F, DBError, T]

  class MongoError(e: DBError) extends RuntimeException with NoStackTrace

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

    def of[T, F[_]](f: => FE[T])(implicit ec: EC, F: LiftIO[F]): Result[F, T] =
      EitherT(F.liftIO(IO.fromFuture(IO(f.recover {
        case e: Throwable => DBException(e).asLeft[T]
      }))))

  }
  /**
   * creates a DAO that use F for error handling directly
   */
  def direct[F[_], A: Format](daoR: SyncEntityDAO[A, F])(implicit ec: EC, F: MonadError[F, Throwable]): EntityDAO[F, A] = {
    type DBResult[T] = EitherT[F, DBError, T]
    val fk = implicitly[FunctorK[EntityDAO[?[_], A]]]
    fk.mapK(daoR)(λ[DBResult ~> F] { dr =>
      dr.value.flatMap(F.fromEither)
    })

  }
}

class IOEntityDAO[T: Format](collection: JSONCollection)(implicit ex: EC) extends SyncEntityDAO[T, IO](collection)


