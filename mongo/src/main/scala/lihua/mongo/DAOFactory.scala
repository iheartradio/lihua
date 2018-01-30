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

/**
 * Should be created one per application
 */
class DAOFactory[F[_]: Async](cfg: Config)(implicit sh: ShutdownHook) {

  val mongoF: F[Mongo] = IO(Mongo(cfg)).liftIO

  def create(dbName: String, collectionName: String)
            (implicit ec: ExecutionContext): F[JSONCollection] = {
    val readPreference = cfg.as[Option[String]]("mongoDB.read-preference." + collectionName).fold[ReadPreference](ReadPreference.primary) {
      case "secondary" => ReadPreference.secondary
      case "primary"   => ReadPreference.primary
    }

    for {
      mongo <- mongoF
      db <- toF(mongo.database(dbName))
    } yield db.jsonCollection(collectionName, readPreference = readPreference)

  }

  def createDAO[RF[_], A: Format](dbName: String,
                                  collectionName: String)(
                                  f: JSONCollection => F[EntityDAO[RF, A]])
                                 (implicit ec: ExecutionContext): F[EntityDAO[RF, A]] =
    create(dbName, collectionName).flatMap(f)

  protected def toF[B](f : => Future[B])(implicit ec: ExecutionContext) : F[B] =
    IO.fromFuture(IO(f)).liftIO
}

