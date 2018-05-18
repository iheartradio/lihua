package lihua
package crypt

import cats.effect.{IO, Sync}
import lihua.mongo.Crypt
import tsec.cipher.symmetric.PlainText
import tsec.common._
import tsec.cipher.symmetric._
import tsec.cipher.symmetric.jca._

import scala.io.StdIn
import cats.implicits._

class CryptTsec[F[_]](key: String)(implicit F: Sync[F]) extends Crypt[F] {

  private val ekeyF: F[SecretKey[AES128CTR]] =
    AES128CTR.buildKey[F](key.base64Bytes)

  implicit val ctrStrategy: IvGen[F, AES128CTR] = AES128CTR.defaultIvStrategy[F]

  def encrypt(value: String): F[String] =
    AES128CTR
      .genEncryptor[F]
      .flatMap(
        implicit instance =>
          for {
            ekey      <- ekeyF
            encrypted <- AES128CTR.encrypt[F](PlainText(value.utf8Bytes), ekey)
          } yield (encrypted.content ++ encrypted.nonce).toB64String
    )

  def decrypt(value: String): F[String] =
    AES128CTR
      .genEncryptor[F]
      .flatMap(
        implicit instance =>
          for {
            ekey <- ekeyF
            toDecrypt <- F.fromEither(AES128CTR.ciphertextFromConcat(value.base64Bytes))
            decrypted <- AES128CTR.decrypt[F](toDecrypt, ekey)
          } yield decrypted.toUtf8String
  )
}


object CryptTsec {
  def apply[F[_]: Sync](key: String): Crypt[F] = new CryptTsec[F](key)

  def genKey[F[_]: Sync] : F[String] =
      AES128CTR.generateKey[F].map(_.getEncoded.toB64String)


  def main(args: Array[String]): Unit = {
    val command = args.headOption match {
      case Some("genKey") => genKey[IO]
      case Some("encrypt") =>
        for {
          key <- IO(StdIn.readLine("Enter your key:"))
          pass <- IO(StdIn.readLine("Enter your text:"))
          r <- CryptTsec[IO](key).encrypt(pass)
        } yield r


      case Some("decrypt") =>
        for {
           key <- IO(StdIn.readLine("Enter your key:"))
           pass <- IO(StdIn.readLine("Enter your text:"))
           r <- CryptTsec[IO](key).decrypt(pass)
        } yield r
      case _ => IO.pure("usage: [genKey|encrypt]")
    }

    command.attempt.map(_.fold(println, println)).unsafeRunSync()
  }
}
