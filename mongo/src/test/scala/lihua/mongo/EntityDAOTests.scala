/*
* Copyright [2017] [iHeartMedia Inc]
* All rights reserved
*/
package lihua
package mongo
import cats.effect.IO
import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}
import play.api.libs.json.{Format, Json}
import reactivemongo.play.json.collection.JSONCollection



class EntityDAOTests extends FunSuite with Matchers {
  test("no side effect before performing unsafe IO with task") {
    import scala.concurrent.ExecutionContext.Implicits.global
    object testDAO extends AsyncEntityDAO[TestEntity, IO](null)

    testDAO.find(Json.obj("1" -> "1")) //nothing should really happens

    succeed
  }

  test("MongoDB driver is created only once") {
    import scala.concurrent.ExecutionContext.Implicits.global
    implicit val ms = ShutdownHook.manual
    implicit val mongoDB = MongoDB[IO](ConfigFactory.parseString(
      """
        |mongoDB {
        |  hosts: ["127.0.0.1:27017"]
        |}
      """.stripMargin), None).unsafeRunSync()

    (for {
      _ <- mongoDB.collection("test", "test")
      _ <- mongoDB.collection("test", "test2")
      _ <- IO(ms.shutdown())
    } yield ()).unsafeRunSync()

    ms.callbacks.size shouldBe 1
    succeed

  }

  test("contramap") {
    cats.Contravariant[EntityDAO[IO, TestEntity, ?]]
  }
}


class TestEntityDAO extends DirectDAOFactory[TestEntity, IO]("test", "test") {
  override protected def ensure(collection: JSONCollection): IO[Unit] = IO.unit
}


case class TestEntity(a: String)
object TestEntity {
  implicit val format: Format[TestEntity] = Json.format[TestEntity]
}
