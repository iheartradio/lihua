package lihua
package mongo

import cats.effect.IO
import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}

class MongoDBTests extends FunSuite with Matchers {
  test("can read example config correctly") {
    val config = new MongoDB[IO](ConfigFactory.load("example.conf")).configF
    config.unsafeRunSync().dbs should not be(empty)
  }
}
