package lihua
package mongo

import com.typesafe.config.{Config, ConfigFactory}
import reactivemongo.api._
import reactivemongo.play.json.collection.JSONCollection
import cats.effect.{Async, IO, Resource, Sync}

import scala.concurrent.{ExecutionContext, Future}
import net.ceedubs.ficus.Ficus._
import cats.implicits._
import net.ceedubs.ficus.readers.namemappers.implicits.hyphenCase
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader

import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import concurrent.duration._
/**
  * A MongoDB instance from config
  * Should be created one per application
  */
class MongoDB[F[_]: Async] private(private[mongo] val config: MongoDB.MongoConfig, connection: MongoConnection, private[mongo] val driver: MongoDriver) {

  private def database(databaseName: String)(implicit ec: ExecutionContext): F[DB] = {
    val dbConfigO = config.dbs.get(s"$databaseName")
    val name = dbConfigO.flatMap(_.name).getOrElse(databaseName)
    toF(connection.database(name))
  }

  def collection(dbName: String, collectionName: String)
            (implicit ec: ExecutionContext): F[JSONCollection] = {

    val collectionConfig = config.dbs.get(dbName).flatMap(_.collections.get(collectionName))
    val name = collectionConfig.flatMap(_.name).getOrElse(collectionName)
    val readPreference = collectionConfig.flatMap(_.readPreference)
    database(dbName).map(
      db => new JSONCollection(db, name, db.failoverStrategy, readPreference.getOrElse(ReadPreference.primary))
    )
  }

  def close(implicit to: FiniteDuration = 2.seconds): F[Unit] =
    Sync[F].delay(driver.close(to))

  protected def toF[B](f : => Future[B])(implicit ec: ExecutionContext) : F[B] =
    IO.fromFuture(IO(f)).to[F]
}


object MongoDB {

  def apply[F[_]](rootConfig: Config, cryptO: Option[Crypt[F]] = None)
                 (implicit F: Async[F],
        sh: ShutdownHook = ShutdownHook.ignore): F[MongoDB[F]] = {

      for {
        config <- F.fromTry(Try{
                    rootConfig.as[MongoConfig]("mongoDB")
                  }).ensure(new MongoDBConfigurationException("mongoDB.hosts must be set in the conf"))(_.hosts.nonEmpty)

        creds  <- credOf(config, cryptO)
        d <-  F.delay(MongoDriver(rootConfig.withFallback(ConfigFactory.load("default-reactive-mongo.conf"))))

      } yield {
        val options = MongoConnectionOptions.default.copy(
          sslEnabled = config.sslEnabled,
          authenticationDatabase = config.authSource,
          sslAllowsInvalidCert = true,
          authenticationMechanism = config.authMode,
          readPreference = config.readPreference.getOrElse(ReadPreference.primaryPreferred),
          failoverStrategy =  FailoverStrategy.default.copy(
            initialDelay = config.initialDelay.getOrElse(FailoverStrategy.default.initialDelay),
            retries = config.retries.getOrElse(FailoverStrategy.default.retries)),
          credentials = creds
        )
        val connection = d.connection(config.hosts, options)
        val mongoDB = new MongoDB(config, connection, d)
        sh.onShutdown(mongoDB.driver.close())
        mongoDB
      }
    }

  def resource[F[_]](rootConfig: Config, cryptO: Option[Crypt[F]] = None)
                 (implicit F: Async[F]): Resource[F, MongoDB[F]] =
    Resource.make(MongoDB(rootConfig, cryptO))(_.close)

  private[mongo] def credOf[F[_]](config: MongoConfig, cryptO: Option[Crypt[F]])
                                 (implicit F: Async[F]) : F[Map[String, MongoConnectionOptions.Credential]] =
    config.dbs.toList.traverse { case (k, dbc) =>
      dbc.credential.traverse { c =>
        cryptO.fold(F.pure(c.password))(_.decrypt(c.password))
          .map(p => (dbc.name.getOrElse(k), MongoConnectionOptions.Credential(c.username, Some(p))))
      }
    }.map { l =>
      val map = l.flatten.toMap
      //if authSource db is missing a credential, use the top one by default.
      (config.authSource, map.values.headOption)
        .tupled
        .fold(map)(Map(_) ++ map)

    }


  class MongoDBConfigurationException(msg: String) extends Exception(msg)

  case class MongoConfig(
    hosts: List[String] = Nil,
    sslEnabled: Boolean = false,
    authSource: Option[String] = None,
    authMode: AuthenticationMode = ScramSha1Authentication,
    dbs: Map[String, DBConfig] = Map(),
    readPreference: Option[ReadPreference],
    initialDelay: Option[FiniteDuration],
    retries: Option[Int]
  )

  case class DBConfig(
    name: Option[String],
    credential: Option[Credential],
    collections: Map[String, CollectionConfig] = Map()
  )

  case class Credential(username: String, password: String)

  case class CollectionConfig(
    name: Option[String],
    readPreference: Option[ReadPreference]
  )

  implicit val readPreferenceValueReader: ValueReader[ReadPreference] = new ValueReader[ReadPreference] {
    def read(config: Config, path: String): ReadPreference = config.getString(path) match {
      case "secondary" => ReadPreference.secondary
      case "secondary-preferred" => ReadPreference.secondaryPreferred
      case "primary" =>  ReadPreference.primary
      case "primary-preferred" =>  ReadPreference.primaryPreferred
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

