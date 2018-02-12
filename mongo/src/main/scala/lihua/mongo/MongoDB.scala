package lihua
package mongo

import com.typesafe.config.{Config, ConfigFactory}
import reactivemongo.api.{DB, MongoConnection, MongoDriver, ReadPreference}
import reactivemongo.play.json.collection.JSONCollection
import cats.effect.{Async, IO}

import scala.concurrent.{ExecutionContext, Future}
import net.ceedubs.ficus.Ficus._
import cats.effect.implicits._
import cats.implicits._
import lihua.mongo.MongoDB.MongoDBConfigurationException
import reactivemongo.core.nodeset.Authenticate

/**
 * Should be created one per application
 */
class MongoDB[F[_]](config: Config)(implicit sh: ShutdownHook, F: Async[F]) {
  private val driver: F[MongoDriver] = F.liftIO(IO {
    val driver = MongoDriver(config.withFallback(ConfigFactory.load("default-reactive-mongo.conf")))
    sh.onShutdown(driver.close())
    driver
  })

  private val connection: F[MongoConnection] = {
    val hosts = config.getOrElse[List[String]]("mongoDB.hosts", Nil)
    if (hosts.isEmpty) F.raiseError(new MongoDBConfigurationException("mongoDB.hosts must be set in the conf"))
    else driver.map(_.connection(hosts))
  }

  private def credentials: F[List[Authenticate]] = ???

  private def database(databaseName: String)(implicit ec: ExecutionContext): F[DB] =
    for {
      c <- connection
      db <- toF(c.database(dbName(databaseName)))
    } yield db


  private def dbName(database: String) =
    config.getOrElse[String](s"mongoDB.dbs.$database", database)


  def collection(dbName: String, collectionName: String)
            (implicit ec: ExecutionContext): F[JSONCollection] = {
    val readPreference = config.as[Option[String]]("mongoDB.read-preference." + collectionName).fold[ReadPreference](ReadPreference.primary) {
      case "secondary" => ReadPreference.secondary
      case "primary"   => ReadPreference.primary
    }

    database(dbName).map(db => new JSONCollection(db, collectionName, db.failoverStrategy, readPreference))
  }

  protected def toF[B](f : => Future[B])(implicit ec: ExecutionContext) : F[B] =
    IO.fromFuture(IO(f)).liftIO
}


object MongoDB {
  class MongoDBConfigurationException(msg: String) extends Exception(msg)

}

