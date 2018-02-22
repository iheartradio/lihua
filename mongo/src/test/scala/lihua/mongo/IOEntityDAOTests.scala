/*
* Copyright [2017] [iHeartMedia Inc]
* All rights reserved
*/
package lihua.mongo
import org.scalatest.{FunSuite, Matchers}
import play.api.libs.json.{Format, Json}

import scala.concurrent.ExecutionContext.Implicits.global

class IOEntityDAOTests extends FunSuite with Matchers {
  test("no side effect before performing unsafe IO with task") {
    object testDAO extends IOEntityDAO[TestEntity](null)

    testDAO.find(Json.obj("1" -> "1")) //nothing should really happens

    succeed
  }
}

case class TestEntity(a: String)
object TestEntity {
  implicit val format: Format[TestEntity] = Json.format[TestEntity]
}
