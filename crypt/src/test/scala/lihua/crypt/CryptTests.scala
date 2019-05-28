package lihua
package crypt

import cats.effect.IO
import org.scalatest.{EitherValues, Matchers}
import org.scalatest.funsuite.AnyFunSuiteLike

class CryptTests extends AnyFunSuiteLike with Matchers with EitherValues {
  type F[A] = IO[A]


  test("identity") {
    val msg = "a test content of my secrete message"
    val result = for {
      key <- CryptTsec.genKey[F]
      c =  CryptTsec[F](key)
      encrypted <- c.encrypt(msg)
      decrypted <- c.decrypt(encrypted)
    } yield decrypted

    result.unsafeRunSync() shouldBe msg
  }

}
