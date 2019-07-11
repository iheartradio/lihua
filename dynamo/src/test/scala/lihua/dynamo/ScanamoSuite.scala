package lihua
package dynamo

import cats.effect.IO
import org.scalatest.Matchers
import org.scalatest.funsuite.AnyFunSuiteLike
import cats.implicits._

case class Farm(animals: List[String])
case class Farmer(name: String, age: Long, farm: Farm)

class ScanamoSuite extends AnyFunSuiteLike with Matchers {

  import org.scanamo._
  import org.scanamo.syntax._
  import org.scanamo.auto._

  val client = LocalDynamoDB.client()
//  import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
//  LocalDynamoDB.createTable(client)("farmer")('name -> S)

  val table = Table[Farmer]("farmer")

   test("a") {
     val ops =
       for {
         _ <- table.putAll(Set(
             Farmer("McDonald", 156L, Farm(List("sheep", "cow"))),
             Farmer("Boggis", 43L, Farm(List("chicken")))
           ))
         mcdonald <- table.get('name -> "McDonald")
       } yield mcdonald

    val o = ScanamoCats[IO](client).exec(ops) >>= (r => IO.delay(println(r)))

     o.unsafeRunAsyncAndForget()

   }

}