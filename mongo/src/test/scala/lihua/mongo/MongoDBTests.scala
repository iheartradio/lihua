package lihua
package mongo

import cats.effect.IO
import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}
import reactivemongo.api.ReadPreference
import reactivemongo.core.nodeset.Authenticate
import concurrent.duration._
class MongoDBTests extends FunSuite with Matchers {

  object mockCrypt extends Crypt[IO] {
    override def encrypt(value: String): IO[String] = ???
    override def decrypt(value: String): IO[String] = IO(value + "decrypted")
  }

  test("can read example config correctly") {
    val mongoDB = MongoDB[IO](ConfigFactory.parseString(
      """
        |mongoDB {
        |  hosts: ["127.0.0.1:3661",  "127.0.0.1:3662"]
        |  ssl-enabled: true
        |  auth-source: admin
        |  initial-delay: 3s
        |  retries: 14
        |  read-preference: secondary
        |  credential: {
        |    username: alf
        |    password: "L+JYLQYA2nADaTT014Uqxvt6ErA9Fsrk77XlDg=="
        |  }
        |  dbs: {
        |    school: {
        |      name: schoolDB
        |      collections: {
        |        student: {
        |          name: studentCollection
        |          read-preference: "primary"
        |        }
        |      }
        |    }
        |  }
        |}
      """.stripMargin), Some(mockCrypt))
    val config = mongoDB.unsafeRunSync().config

    config.dbs should not be(empty)
    config.sslEnabled shouldBe true
    config.authSource shouldBe Some("admin")
    config.retries shouldBe Some(14)
    config.initialDelay shouldBe Some(3.seconds)
    config.readPreference shouldBe Some(ReadPreference.secondary)
    MongoDB.authOf(config, Some(mockCrypt)).unsafeRunSync().head shouldBe Authenticate("admin", "alf", "L+JYLQYA2nADaTT014Uqxvt6ErA9Fsrk77XlDg==decrypted")

  }

  test("can shutdown driver successfully") {
    import scala.concurrent.ExecutionContext.Implicits.global
    implicit val ms = ShutdownHook.manual
    val mongoDB = MongoDB[IO](ConfigFactory.parseString(
      """
        |mongoDB {
        |  hosts: ["127.0.0.1:27017"]
        |}
      """.stripMargin), Some(mockCrypt)).unsafeRunSync()

    val process = for {
      _ <- mongoDB.collection("test", "test")
      _ <- IO(ms.shutdown())
     } yield ()

    process.unsafeRunSync()
    succeed
  }

}

