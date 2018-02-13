package lihua.mongo

import org.scalatest.{FunSuite, Matchers}
//import IntegrationTests._
import cats.effect.IO
import com.typesafe.config.ConfigFactory
class IntegrationTests extends FunSuite with Matchers {
  test("load db with encrypted credentials") {
    val config = ConfigFactory.parseString(
      """
        |mongoDB {
        |  hosts: ["127.0.0.1:27012"]
        |  dbs: {
        |    school: {
        |      name: lihuaTestSchoolDB
        |      credential: {
        |        username: alf
        |        password: "Pf+A4F4VrwpOUNjJd1eBxkH7dFfblrYdIw=="
        |      }
        |      collections: {}
        |    }
        |  }
        |}
        |
      """.stripMargin)
    val db = new MongoDB[IO](config, cryptO = Some(new Crypt("ylzjrLcVTNoocjMxL+jOSA==")))

    db.configF.flatMap(cfg => db.credentials(cfg.dbs)).unsafeRunSync().head.password shouldBe "password1"
  }


}



object IntegrationTests {

}
