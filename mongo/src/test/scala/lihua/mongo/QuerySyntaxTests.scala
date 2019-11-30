package lihua
package mongo

import cats.effect.IO
import org.scalatest.funsuite.AnyFunSuiteLike
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.matchers.should.Matchers

class QuerySyntaxTests extends AnyFunSuiteLike with Matchers {
  object testDAO extends AsyncEntityDAO[TestEntity, IO](null)

  test("tuples") {
    testDAO.find('a -> "1")
    testDAO.find(('a -> 1, 'b -> "d"))
    testDAO.findOne(('a -> 1, 'b -> "d"))
    testDAO.findOne(('a -> 1, 'b -> 2.0, 'c -> "dfd", 'd -> true))

    (('a -> 1, 'b -> "d"):Query) shouldBe (Json.obj("a" -> 1, "b" -> "d"):Query)
    succeed
  }
}
