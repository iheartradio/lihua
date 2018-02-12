package lihua.mongo

import cats.MonadError
import tsec.common._
import tsec.cipher.symmetric._
import tsec.cipher.symmetric.imports._
import cats.implicits._

class Crypt[F[_]](key: String)(implicit F: MonadError[F, Throwable]) {

  private def F[A](either: Either[Throwable, A]): F[A] = F.fromEither(either)

  private val instanceF = F(DefaultEncryptor.instance)
  private val ekeyF: F[SecretKey[AES128]] =
    F(DefaultEncryptor.keyGen.buildKey(key.base64Bytes))

  def encrypt(value: String): F[String] = {
    for {
     instance  <- instanceF
     ekey      <- ekeyF
     encrypted <- F(instance.encrypt(PlainText(value.utf8Bytes), ekey))
    } yield (encrypted.content ++ encrypted.iv).toB64String
  }

  def decrypt(value: String): F[String] = {
    for {
      instance <- instanceF
      ekey <- ekeyF
      toDecrypt <- F(DefaultEncryptor.fromSingleArray(value.base64Bytes))
      decrypted <- F(instance.decrypt(toDecrypt, ekey))
    } yield decrypted.content.toUtf8String
  }
}


object Crypt {
  def genKey[F[_]](implicit F: MonadError[F, Throwable]) : F[String] =
    F.fromEither(
      DefaultEncryptor.keyGen.generateKey().map(_.getEncoded.toB64String)
    )


}
