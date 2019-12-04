package lihua
package mongo

import cats.effect.{Async, IO}
import play.api.libs.json.Format
import reactivemongo.play.json.collection.JSONCollection
import cats.implicits._
import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.collection.BSONSerializationPack
import reactivemongo.api.commands.CommandError
import reactivemongo.api.indexes.{Index, IndexType}

import scala.concurrent.ExecutionContext

trait DAOFactory[F[_], DAOF[_], A] {
  def create(
      implicit mongoDB: MongoDB[F],
      ec: ExecutionContext
    ): F[EntityDAO[DAOF, A, Query]]
}

abstract class DAOFactoryWithEnsure[A: Format, DAOF[_], F[_]](
    dbName: String,
    collectionName: String
  )(implicit F: Async[F])
    extends DAOFactory[F, DAOF, A] {

  protected def ensure(collection: JSONCollection): F[Unit]

  private def ensureCollection(
      collection: JSONCollection
    )(implicit ec: ExecutionContext
    ): F[Unit] = {
    implicit val cs = IO.contextShift(ec)
    F.liftIO(IO.fromFuture(IO(collection.create().recover {
      case CommandError.Code(48 /*NamespaceExists*/ ) => ()
    })))
  }

  def create(
      implicit mongoDB: MongoDB[F],
      ec: ExecutionContext
    ): F[EntityDAO[DAOF, A, Query]] = {
    for {
      c <- mongoDB.collection(dbName, collectionName)
      _ <- ensureCollection(c)
      _ <- ensure(c)
      dao <- doCreate(c)
    } yield dao
  }

  def doCreate(
      c: JSONCollection
    )(implicit ec: ExecutionContext
    ): F[EntityDAO[DAOF, A, Query]]

  //replacement for the deprecated Index.apply
  protected def index(
      key: Seq[(String, IndexType)],
      name: Option[String] = None,
      unique: Boolean = false,
      background: Boolean = false,
      sparse: Boolean = false,
      version: Option[Int] = None // let MongoDB decide
    ): Index =
    Index(BSONSerializationPack)(
      key,
      name,
      unique,
      background,
      false,
      sparse,
      version,
      None,
      BSONDocument.empty
    )
}

abstract class DirectDAOFactory[A: Format, F[_]](
    dbName: String,
    collectionName: String
  )(implicit F: Async[F])
    extends DAOFactoryWithEnsure[A, F, F](dbName, collectionName) {

  def doCreate(
      c: JSONCollection
    )(implicit ec: ExecutionContext
    ): F[EntityDAO[F, A, Query]] =
    F.delay(AsyncEntityDAO.direct[F, A](new AsyncEntityDAO(c)))
}

abstract class EitherTDAOFactory[A: Format, F[_]](
    dbName: String,
    collectionName: String
  )(implicit F: Async[F])
    extends DAOFactoryWithEnsure[A, AsyncEntityDAO.Result[F, ?], F](
      dbName,
      collectionName
    ) {

  def doCreate(
      c: JSONCollection
    )(implicit ec: ExecutionContext
    ): F[EntityDAO[AsyncEntityDAO.Result[F, ?], A, Query]] =
    F.pure(new AsyncEntityDAO[A, F](c))
}
