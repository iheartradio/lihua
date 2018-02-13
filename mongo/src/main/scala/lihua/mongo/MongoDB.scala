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
import lihua.mongo.MongoDB._
import net.ceedubs.ficus.readers.namemappers.implicits.hyphenCase
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader
import reactivemongo.core.nodeset.Authenticate

import scala.util.Try

/**
  * A MongoDB instance from config
  * Should be created one per application
  */
class MongoDB[F[_]](rootConfig: Config, cryptO: Option[Crypt[F]] = None)(
  implicit F: Async[F],
          sh: ShutdownHook = ShutdownHook.ignore
  ){

  val configF = F.suspend(F.fromTry(Try(rootConfig.as[MongoConfig]("mongoDB"))))

  private val driver: F[MongoDriver] =
    F.delay {
      MongoDriver(rootConfig.withFallback(ConfigFactory.load("default-reactive-mongo.conf")))
    }.flatTap(d => F.pure(sh.onShutdown(d.close())))

  def shutdown(): F[Unit] = driver.map(_.close()).void

  private val connection: F[MongoConnection] = for {
    config <- configF
    auths <- credentials(config.dbs)
    c <- if (config.hosts.isEmpty)
           F.raiseError(new MongoDBConfigurationException("mongoDB.hosts must be set in the conf"))
        else driver.map(_.connection(nodes = config.hosts, authentications = auths))
  } yield c

  private def database(databaseName: String)(implicit ec: ExecutionContext): F[DB] =
    for {
      c <- connection
      config <- configF
      dbConfigO = config.dbs.get(s"$databaseName")
      name = dbConfigO.flatMap(_.name).getOrElse(databaseName)
      db <- toF(c.database(name))
    } yield db

  private[mongo] def credentials(dbCfgs: Map[String, DBConfig]): F[List[Authenticate]] = {
     dbCfgs.toList.collect {
       case (name, DBConfig(dbName, _, Some(Credential(username, password)))) =>
         (dbName.getOrElse(name), username, password)
     }.traverse {
       case (dbName, username, password) =>
         cryptO.fold(F.pure(password))(_.decrypt(password)).map(Authenticate(dbName, username, _))
     }
  }

  def collection(dbName: String, collectionName: String)
            (implicit ec: ExecutionContext): F[JSONCollection] = for {
    config <- configF
    collectionConfig = config.dbs.get(dbName).flatMap(_.collections.get(collectionName))
    name = collectionConfig.flatMap(_.name).getOrElse(collectionName)
    readPreference = collectionConfig.flatMap(_.readPreference)
    db <- database(dbName)
  } yield
    new JSONCollection(db, name, db.failoverStrategy, readPreference.getOrElse(ReadPreference.primary))

  protected def toF[B](f : => Future[B])(implicit ec: ExecutionContext) : F[B] =
    IO.fromFuture(IO(f)).liftIO
}


object MongoDB {
  class MongoDBConfigurationException(msg: String) extends Exception(msg)

  case class MongoConfig(
    hosts: List[String],
    dbs: Map[String, DBConfig]
  )

  case class DBConfig(
    name: Option[String],
    collections: Map[String, CollectionConfig],
    credential: Option[Credential]
  )

  case class Credential(username: String, password: String)

  case class CollectionConfig(
    name: Option[String],
    readPreference: Option[ReadPreference]
  )

  implicit val readPreferenceValueReader: ValueReader[ReadPreference] = new ValueReader[ReadPreference] {
    def read(config: Config, path: String): ReadPreference = config.getString(path) match {
      case "secondary" => ReadPreference.secondary
      case "primary" =>  ReadPreference.primary
      case s => throw new MongoDBConfigurationException(s + " is not a recoganized read preference")
    }
  }
}

