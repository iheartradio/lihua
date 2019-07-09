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
import cats.effect.{Async, IO}
import cats.{MonadError, ~>}
import lihua.mongo.AsyncEntityDAO.Result
import cats.tagless.FunctorK
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.api.Cursor.ErrorHandler
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.BSONObjectID


import scala.util.control.NoStackTrace
import JsonFormats._
import lihua.EntityDAO.EntityDAOMonad


class AsyncEntityDAO[T: Format, F[_]: Async](collection: JSONCollection)(implicit ex: EC)
  extends EntityDAOMonad[AsyncEntityDAO.Result[F, ?], T, Query] {
  type R[A] = AsyncEntityDAO.Result[F, A]
  import AsyncEntityDAO.Result._

  lazy val writeCollection = collection.withReadPreference(ReadPreference.primary) //due to a bug in ReactiveMongo

  def get(id: EntityId): R[Entity[T]] = of(
    collection.find(Query.idSelector(id), none[JsObject]).one[Entity[T]]
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

  private def internalFind(q: Query): Future[Vector[Entity[T]]] =
    builder(q).cursor[Entity[T]](readPref(q)).
      collect[Vector](-1, errorHandler)

  private def readPref(q: Query) = q.readPreference.getOrElse(collection.readPreference)

  private def builder(q: Query) = {
    var builder = collection.find(q.selector, q.projection)
    builder = q.hint.fold(builder)(builder.hint)
    builder = q.opts.fold(builder)(builder.options)
    builder = q.sort.fold(builder)(builder.sort)
    builder
  }

  def insert(t: T): R[Entity[T]] = {
    val entity = Entity(BSONObjectID.generate.stringify, t)
    of {
      writeCollection.insert(ordered = false).one(entity)
    }.ensureOr(UpdatedCountErrorDetail(1, _))(_ == 1).as(entity)
  }

  def remove(id: EntityId): R[Unit] =
    removeAll(Query.idSelector(id)).ensure(NotFound)(_ > 0).void

  def upsert(entity: Entity[T]): R[Entity[T]] =
    update(Query.idSelector(entity._id), entity, true).as(entity)

  def update(entity: Entity[T]): R[Entity[T]] =
    update(Query.idSelector(entity._id), entity, false).as(entity)

  def update(q: Query, entity: Entity[T], upsert: Boolean): R[Boolean] = of {
    writeCollection.update(ordered = false).one(q.selector, entity, upsert = upsert, multi = false)
  }.ensureOr(UpdatedCountErrorDetail(1, _))(_ <= 1).map(_ == 1)

  def removeAll(q: Query): R[Int] = of {
    writeCollection.delete().one(q.selector)
  }

  private val errorHandler: ErrorHandler[Vector[Entity[T]]] = Cursor.FailOnError()

  def of[A](f: => Future[Either[DBError, A]]): Result[F, A] =
    EitherT(Async[F].liftIO(IO.fromFuture(IO(f.recover {
      case l: reactivemongo.api.commands.LastError => DBLastError(l.message).asLeft[A]
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
        wr.writeConcernError.toList.map(e => WriteConcernErrorDetail(e.code, e.errmsg))
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

