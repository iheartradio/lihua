package lihua.mongo

import cats.effect.IO
import play.api.libs.json.Format
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.ExecutionContext

trait DAOFactory[F[_], DAOF[_], A] {
  def create(implicit mongoDB: MongoDB[F], ec: ExecutionContext): F[EntityDAO[DAOF, A]]
}


abstract class IODAOFactory[A :Format, F[_]](dbName: String, collectionName: String)
  extends DAOFactory[IO, F, A] {

  protected def ensure(collection: JSONCollection): IO[Unit]

  def create(implicit mongoDB: MongoDB[IO], ec: ExecutionContext): IO[EntityDAO[F, A]] = {
    for {
      c <- mongoDB.collection(dbName, collectionName)
      _ <- ensure(c)
      dao <- doCreate(c)
    } yield dao
  }

  def doCreate(c: JSONCollection)(implicit ec: ExecutionContext): IO[EntityDAO[F, A]]
}


abstract class IODirectDAOFactory[A: Format](dbName: String, collectionName: String)
                              extends IODAOFactory[A, IO](dbName, collectionName) {

  def doCreate(c: JSONCollection)(implicit ec: ExecutionContext): IO[EntityDAO[IO, A]] =
    IO(IOEntityDAO.direct[IO, A](new IOEntityDAO(c)))


}

abstract class IOEitherTDAOFactory[A: Format](dbName: String, collectionName: String)
                              extends IODAOFactory[A, IOEntityDAO.Result](dbName, collectionName) {
  def doCreate(c: JSONCollection)(implicit ec: ExecutionContext): IO[IOEntityDAO[A]] =
    IO(new IOEntityDAO(c))
}
