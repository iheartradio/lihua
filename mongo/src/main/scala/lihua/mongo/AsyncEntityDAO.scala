/*
* Copyright [2017] [iHeartMedia Inc]
* All rights reserved
*/
package lihua
package mongo

import cats.data.{EitherT, NonEmptyList}
import lihua.mongo.DBError._
import play.api.libs.json.{Format}
import reactivemongo.play.json.collection.JSONCollection
import reactivemongo.play.json._
import cats.implicits._

import scala.concurrent.{Future, ExecutionContext => EC}
import cats.effect.{Async, IO}
import cats.{MonadError, ~>}
import lihua.mongo.AsyncEntityDAO.Result
import cats.tagless.FunctorK
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.api.Cursor.ErrorHandler
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.BSONObjectID

import scala.concurrent.duration.Duration
import scalacache.Cache
import scalacache.cachingF
import scalacache.modes.scalaFuture._
import scalacache.caffeine._

import scala.util.control.NoStackTrace
import JsonFormats._


class AsyncEntityDAO[T: Format, F[_]: Async](collection: JSONCollection)(implicit ex: EC)
  extends EntityDAO[AsyncEntityDAO.Result[F, ?], T, Query] {
  type R[A] = AsyncEntityDAO.Result[F, A]
  import AsyncEntityDAO.Result._

  implicit val scalaCache: Cache[Vector[Entity[T]]] = CaffeineCache[Vector[Entity[T]]]

  lazy val writeCollection = collection.withReadPreference(ReadPreference.primary) //due to a bug in ReactiveMongo

  def get(id: ObjectId): R[Entity[T]] = of(
    collection.find(Query.idSelector(id)).one[Entity[T]]
  )

  def find(q: Query): R[Vector[Entity[T]]] = of {
    internalFind(q)
  }

  def findOne(q: Query): R[Entity[T]] = of {
    builder(q).one[Entity[T]](readPref(q))
  }

  def findOneOption(q: Query): R[Option[Entity[T]]] = of {
    builder(q).one[Entity[T]](readPref(q))
  }

  def invalidateCache(q: Query): R[Unit] =
    of(scalacache.remove(q)).void

  private def internalFind(q: Query): Future[Vector[Entity[T]]] = {

    builder(q).cursor[Entity[T]](readPref(q)).
      collect[Vector](-1, errorHandler)
  }

  private def readPref(q: Query) = q.readPreference.getOrElse(collection.readPreference)

  private def builder(q: Query) = {
    var builder = collection.find(q.selector)
    builder = q.hint.fold(builder)(builder.hint)
    builder = q.opts.fold(builder)(builder.options)
    builder = q.sort.fold(builder)(builder.sort)
    builder
  }

  def findCached(query: Query, ttl: Duration): R[Vector[Entity[T]]] = of {
    if (ttl.length == 0L)
      internalFind(query)
    else
      cachingF(query)(Some(ttl)) {
        internalFind(query)
      }
  }

  def insert(t: T): R[Entity[T]] = of {
    val entity = Entity(BSONObjectID.generate.stringify, t)
    writeCollection.insert(entity).map(parseWriteResult(_).as(entity))
  }

  def remove(id: ObjectId): R[Unit] =
    removeAll(Query.idSelector(id)).ensure(NotFound)(_ > 0).void

  def upsert(entity: Entity[T]): R[Entity[T]] =
    update(Query.idSelector(entity._id), entity, true)

  def update(entity: Entity[T]): R[Entity[T]] =
    update(Query.idSelector(entity._id), entity, false)


  def update(q: Query, entity: Entity[T], upsert: Boolean): R[Entity[T]] = of {
    writeCollection.update(q.selector, entity, upsert = upsert)
  }.as(entity)

  def removeAll(q: Query): R[Int] = of {
    writeCollection.remove(q.selector)
  }

  private val errorHandler: ErrorHandler[Vector[Entity[T]]] = Cursor.FailOnError()

  def of[A](f: => Future[Either[DBError, A]]): Result[F, A] =
    EitherT(Async[F].liftIO(IO.fromFuture(IO(f.recover {
      case e: Throwable => DBException(e, collection.name).asLeft[A]
    }))))
}

object AsyncEntityDAO {
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
      val errs: List[WriteErrorDetail] =
        wr.writeErrors.toList.map(e => ItemWriteErrorDetail(e.code, e.errmsg)) ++
        wr.writeConcernError.toList.map(e => WriteConcernErrorDetail(e.code, e.errmsg)) ++
        (if(wr.n == 0) List(UpdatedCountErrorDetail) else Nil)
      NonEmptyList.fromList(errs).map(WriteError).toLeft(wr.n)
    }



  }
  /**
   * creates a DAO that use F for error handling directly
   */
  def direct[F[_], A: Format](daoR: AsyncEntityDAO[A, F])(implicit ec: EC, F: MonadError[F, Throwable]): EntityDAO[F, A, Query] = {
    type DBResult[T] = EitherT[F, DBError, T]
    val fk = implicitly[FunctorK[EntityDAO[?[_], A, Query]]]
    fk.mapK(daoR)(λ[DBResult ~> F] { dr =>
      dr.value.flatMap(F.fromEither)
    })

  }
}

