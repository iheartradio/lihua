package lihua.mongo

import cats.effect.{Async, IO}
import play.api.libs.json.Format
import reactivemongo.play.json.collection.JSONCollection
import cats.implicits._
import reactivemongo.api.commands.CommandError

import scala.concurrent.ExecutionContext

trait DAOFactory[F[_], DAOF[_], A] {
  def create(implicit mongoDB: MongoDB[F], ec: ExecutionContext): F[EntityDAO[DAOF, A]]
}

abstract class DAOFactoryWithEnsure[A :Format, DAOF[_], F[_]](
  dbName: String, collectionName: String)
  (implicit F: Async[F])
  extends DAOFactory[F, DAOF, A] {

  protected def ensure(collection: JSONCollection): F[Unit]

  private def ensureCollection(collection: JSONCollection)(implicit  ec: ExecutionContext): F[Unit] =
    F.liftIO(IO.fromFuture(IO(collection.create().recover {
      case CommandError.Code(48 /*NamespaceExists*/ ) => ()
    })))


  def create(implicit mongoDB: MongoDB[F], ec: ExecutionContext): F[EntityDAO[DAOF, A]] = {
    for {
      c <- mongoDB.collection(dbName, collectionName)
      _ <- ensureCollection(c)
      _ <- ensure(c)
      dao <- doCreate(c)
    } yield dao
  }

  def doCreate(c: JSONCollection)(implicit ec: ExecutionContext): F[EntityDAO[DAOF, A]]
}


abstract class DirectDAOFactory[A: Format, F[_]](dbName: String, collectionName: String)
                                                (implicit F: Async[F])
  extends DAOFactoryWithEnsure[A, F, F](dbName, collectionName) {

  def doCreate(c: JSONCollection)(implicit ec: ExecutionContext): F[EntityDAO[F, A]] =
    F.delay(SyncEntityDAO.direct[F, A](new SyncEntityDAO(c)))
}

abstract class IODAOFactory[A :Format, DAOF[_]](dbName: String, collectionName: String)
  extends DAOFactoryWithEnsure[A, DAOF, IO](dbName, collectionName)


abstract class IODirectDAOFactory[A: Format](dbName: String, collectionName: String)
                              extends DirectDAOFactory[A, IO](dbName, collectionName)

abstract class IOEitherTDAOFactory[A: Format](dbName: String, collectionName: String)
                              extends IODAOFactory[A, SyncEntityDAO.Result[IO, ?]](dbName, collectionName) {
  def doCreate(c: JSONCollection)(implicit ec: ExecutionContext): IO[IOEntityDAO[A]] =
    IO(new IOEntityDAO(c))
}
