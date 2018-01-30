package lihua
package mongo

import com.typesafe.config.Config
import play.api.libs.json.Format
import reactivemongo.api.ReadPreference
import reactivemongo.play.json.collection.JSONCollection
import Mongo._
import cats.effect.{Async, IO}

import scala.concurrent.{ExecutionContext, Future}
import net.ceedubs.ficus.Ficus._
import cats.effect.implicits._
import cats.implicits._

trait DAOFactory[F[_], A] {

  def ensure(collection: JSONCollection)(implicit ec: ExecutionContext): F[Unit]
  def createFromCollection(collection: JSONCollection)(implicit ec: ExecutionContext): EntityDAO[F, A]

  def create(cfg: Config, dbName: String, collectionName: String)(implicit ec: ExecutionContext,
                                                 sh: ShutdownHook,
                                                 F: Async[F],
                                                 formatT: Format[A]): F[EntityDAO[F, A]] = {
    val readPreference = cfg.as[Option[String]]("mongoDB.read-preference." + collectionName).fold[ReadPreference](ReadPreference.primary) {
      case "secondary" => ReadPreference.secondary
      case "primary"   => ReadPreference.primary
    }

    for {
      db <- toF(Mongo(cfg).database(dbName))
      collection = db.jsonCollection(collectionName, readPreference = readPreference)
      _ <- ensure(collection)
    } yield createFromCollection(collection)
  }



  protected def toF[B](f : => Future[B])(implicit F: Async[F], ec: ExecutionContext) : F[B] =
    IO.fromFuture(IO(f)).liftIO
}

trait IODAOFactory[A] extends DAOFactory[IO, A] {
  def createFromCollection(collection: JSONCollection)(implicit ec: ExecutionContext): EntityDAO[IO, A] = IOEntityDAO[A]
}
