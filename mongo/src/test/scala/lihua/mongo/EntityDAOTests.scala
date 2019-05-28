/*
* Copyright [2017] [iHeartMedia Inc]
* All rights reserved
*/
package lihua
package mongo
import cats.effect.IO
import com.typesafe.config.ConfigFactory
import org.scalatest.Matchers
import org.scalatest.funsuite.AnyFunSuiteLike
import play.api.libs.json.{Format, Json}
import reactivemongo.play.json.collection.JSONCollection
import scala.concurrent.ExecutionContext.Implicits.global



class EntityDAOTests extends AnyFunSuiteLike with Matchers {
  test("no side effect before performing unsafe IO with task") {
    object testDAO extends AsyncEntityDAO[TestEntity, IO](null)

    testDAO.find(Json.obj("1" -> "1")) //nothing should really happens

    succeed
  }

  lazy val localConfig = ConfigFactory.parseString(
      """
        |mongoDB {
        |  hosts: ["127.0.0.1:27017"]
        |}
      """.stripMargin)

  test("MongoDB driver is created only once") {
    implicit val ms = ShutdownHook.manual
    implicit val mongoDB = MongoDB[IO](localConfig, None).unsafeRunSync()

    (for {
      _ <- mongoDB.collection("test", "test")
      _ <- mongoDB.collection("test", "test2")
      _ <- IO(ms.shutdown())
    } yield ()).unsafeRunSync()

    ms.callbacks.size shouldBe 1
    succeed

  }

  test("CRUD smoke") {
    val (retrieved, tryRetrieve) = MongoDB.resource[IO](localConfig).use { implicit mongoDB =>
      for {
        dao <- TestEntityDAOFactory.create
        inserted <- dao.insert(TestEntity("blah"))
        retrieved <- dao.get(inserted._id)
        _ <- dao.remove(inserted._id)
        tryFind <- dao.findOneOption(Query.idSelector(inserted._id))
      } yield (retrieved, tryFind)
    }.unsafeRunSync()

    retrieved.data.a shouldBe "blah"
    tryRetrieve shouldBe empty
  }

  test("contramap") {
    cats.Contravariant[EntityDAO[IO, TestEntity, ?]]
  }
}


object TestEntityDAOFactory extends DirectDAOFactory[TestEntity, IO]("test", "test") {
  override protected def ensure(collection: JSONCollection): IO[Unit] = IO.unit
}


case class TestEntity(a: String)
object TestEntity {
  implicit val format: Format[TestEntity] = Json.format[TestEntity]
}
