package lihua.mongo

import mainecoon.{autoFunctorK, finalAlg}

@autoFunctorK(autoDerivation = true) @finalAlg
trait Crypt[F[_]] {
  def encrypt(value: String): F[String]
  def decrypt(value: String): F[String]
}
