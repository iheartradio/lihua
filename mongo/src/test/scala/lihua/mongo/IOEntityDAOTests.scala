/*
* Copyright [2017] [iHeartMedia Inc]
* All rights reserved
*/
package lihua.mongo
import cats.effect.IO
import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}
import play.api.libs.json.{Format, Json}
import reactivemongo.play.json.collection.JSONCollection


class IOEntityDAOTests extends FunSuite with Matchers {
  test("no side effect before performing unsafe IO with task") {
    import scala.concurrent.ExecutionContext.Implicits.global
    object testDAO extends IOEntityDAO[TestEntity](null)

    testDAO.find(Json.obj("1" -> "1")) //nothing should really happens

    succeed
  }

  test("MongoDB driver is created only once") {
    implicit val ms = ShutdownHook.manual
    implicit val mongoDB = new MongoDB[IO](ConfigFactory.parseString(
      """
        |mongoDB {
        |  hosts: ["127.0.0.1:27017"]
        |}
      """.stripMargin), None)

    (for {
      _ <- mongoDB.driver
      _ <- mongoDB.driver
      _ <- IO(ms.shutdown())
    } yield ()).unsafeRunSync()

    ms.callbacks.size shouldBe 1
    succeed

  }
}


class TestEntityDAO extends IODirectDAOFactory[TestEntity]("test", "test") {
  override protected def ensure(collection: JSONCollection): IO[Unit] = IO.unit
}


case class TestEntity(a: String)
object TestEntity {
  implicit val format: Format[TestEntity] = Json.format[TestEntity]
}
