package lihua.mongo

import org.scalatest.{FunSuite, Matchers}
import scala.concurrent.ExecutionContext.Implicits.global

class QuerySyntaxTests extends FunSuite with Matchers {
  object testDAO extends IOEntityDAO[TestEntity](null)

  test("tuples") {
    testDAO.find('a -> "1")
    testDAO.find(('a -> 1, 'b -> "d"))
    testDAO.findOne(('a -> 1, 'b -> "d"))
    testDAO.findOne(('a -> 1, 'b -> 2.0, 'c -> "dfd", 'd -> true))
    succeed
  }
}
