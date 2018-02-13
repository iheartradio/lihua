package lihua.mongo

import cats.implicits._
import org.scalatest.{EitherValues, FunSuite, Matchers}

class CryptTests extends FunSuite with Matchers with EitherValues {
  type F[A] = Either[Throwable, A]


  test("identity") {
    val msg = "a test content of my secrete message"
    val result = for {
      key <- Crypt.genKey[F]
      c = new Crypt[F](key)
      encrypted <- c.encrypt(msg)
      decrypted <- c.decrypt(encrypted)
    } yield decrypted

    result shouldBe Right(msg)
  }

}
