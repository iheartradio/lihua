package lihua.mongo

import cats.MonadError
import tsec.common._
import tsec.cipher.symmetric._
import tsec.cipher.symmetric.imports._
import cats.implicits._

import scala.io.StdIn
import scala.util.Try

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

  def main(args: Array[String]): Unit = {
    val command = args.headOption.getOrElse("usage: [genKey|encrypt]")
    command match {
      case "genKey" => genKey[Try].fold(println, println)
      case "encrypt" =>
        val key = StdIn.readLine("Enter your key:")
        val pass = StdIn.readLine("Enter your text:")
        new Crypt[Try](key).encrypt(pass).fold(println, println)
      case "decrypt" =>
        val key = StdIn.readLine("Enter your key:")
        val pass = StdIn.readLine("Enter your text:")
        new Crypt[Try](key).decrypt(pass).fold(println, println)
    }
  }
}
