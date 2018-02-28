package lihua
package mongo

import com.typesafe.config.{Config, ConfigFactory}
import reactivemongo.api._
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
          sh: ShutdownHook = ShutdownHook.ignore){

  val configF = F.suspend(F.fromTry(Try(rootConfig.as[MongoConfig]("mongoDB"))))

  private val driver: F[MongoDriver] =
    F.delay {
      MongoDriver(rootConfig.withFallback(ConfigFactory.load("default-reactive-mongo.conf")))
    }.flatTap(d => F.pure(sh.onShutdown(d.close())))

  private val connection: F[MongoConnection] = for {
    config <- configF
    auths <- credentials(config)
    c <- if (config.hosts.isEmpty)
           F.raiseError[MongoConnection](new MongoDBConfigurationException("mongoDB.hosts must be set in the conf"))
        else driver.map(_.connection(
      nodes = config.hosts,
      authentications = auths.toSeq,
      options = MongoConnectionOptions(
        sslEnabled = config.sslEnabled,
        authSource = config.authSource,
        authMode = config.authMode
      )
    ))
  } yield c

  private def database(databaseName: String)(implicit ec: ExecutionContext): F[DB] =
    for {
      c <- connection
      config <- configF
      dbConfigO = config.dbs.get(s"$databaseName")
      name = dbConfigO.flatMap(_.name).getOrElse(databaseName)
      db <- toF(c.database(name))
    } yield db

  private[mongo] def credentials(cfg: MongoConfig): F[Option[Authenticate]] = {
     cfg.credential.traverse { c =>
       cryptO.fold(F.pure(c.password))(_.decrypt(c.password)).map(Authenticate(cfg.authSource.getOrElse("admin"), c.username, _))
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
    hosts: List[String] = Nil,
    sslEnabled: Boolean = false,
    authSource: Option[String] = None,
    credential: Option[Credential] = None,
    authMode: AuthenticationMode = ScramSha1Authentication,
    dbs: Map[String, DBConfig] = Map()
  )

  case class DBConfig(
    name: Option[String],
    collections: Map[String, CollectionConfig],
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
      case s => throw new MongoDBConfigurationException(s + " is not a recognized read preference")
    }
  }

  implicit val authenticationModeReader: ValueReader[AuthenticationMode] = new ValueReader[AuthenticationMode] {
    def read(config: Config, path: String): AuthenticationMode = config.getString(path) match {
      case "CR" => CrAuthentication
      case "SCRAM-SHA-1" =>  ScramSha1Authentication
      case s => throw new MongoDBConfigurationException(s + " is not a recognized Authentication Mode, Options are: CR, SCRAM-SHA-1")
    }
  }
}

