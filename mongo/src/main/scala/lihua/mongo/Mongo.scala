/*
* Copyright [2017] [iHeartMedia Inc]
* All rights reserved
*/
package lihua.mongo

import lihua.mongo.Mongo.MongoDBConfigurationException
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._
import reactivemongo.api._
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}

/**
 * Convenience class that reads mongodb conf from a `Config` and make sure connections are close when shutting down.
 * You can call {@link lihua.mongo.Mongo#database} to create an instance of database and call `db.collection[TC](collectionName)`
 * or you can get the {@link lihua.mongo.Mongo#driver} or {@link lihua.mongo.Mongo#connection} and work with them.
 */
class Mongo private (config: Config)(implicit sh: ShutdownHook) {
  lazy val driver: MongoDriver = {
    val driver = MongoDriver(config.withFallback(ConfigFactory.load("default-reactive-mongo.conf")))
    sh.onShutdown(driver.close())
    driver
  }

  lazy val connection: MongoConnection = {
    val hosts = config.getOrElse[List[String]]("mongoDB.hosts", Nil)
    if (hosts.isEmpty) throw new MongoDBConfigurationException("mongoDB.hosts must be set in the conf")
    driver.connection(hosts)
  }

  def database(databaseName: String)(implicit ec: ExecutionContext): Future[DB] =
    connection.database(dbName(databaseName))

  private def dbName(database: String) =
    config.getOrElse[String](s"mongoDB.dbs.$database", database)
}

object Mongo {

  class MongoDBConfigurationException(msg: String) extends Exception(msg)

  /**
   * Creates a mongo driver wrapper. Please avoid creating multiple instances of Mongo unless necessary,
   * since each instance will create its own `MongoDriver` and/or `MongoConnection` if needed.
   */
  def apply(config: Config)(implicit sh: ShutdownHook) = new Mongo(config)

  implicit class DBOps(db: DB) {
    def jsonCollection(
      name:             String,
      failoverStrategy: FailoverStrategy = db.failoverStrategy,
      readPreference:   ReadPreference   = db.defaultReadPreference
    ): JSONCollection =
      new JSONCollection(db, name, failoverStrategy, readPreference)
  }

}
