package lihua
package mongo

import org.scalatest.{FunSuite, Matchers}
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global

class QuerySyntaxTests extends FunSuite with Matchers {
  object testDAO extends IOEntityDAO[TestEntity](null)

  test("tuples") {
    testDAO.find('a -> "1")
    testDAO.find(('a -> 1, 'b -> "d"))
    testDAO.findOne(('a -> 1, 'b -> "d"))
    testDAO.findOne(('a -> 1, 'b -> 2.0, 'c -> "dfd", 'd -> true))

    (('a -> 1, 'b -> "d"):Query) shouldBe (Json.obj("a" -> 1, "b" -> "d"):Query)
    succeed
  }
}
