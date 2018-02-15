package lihua.mongo

trait Crypt[F[_]] {
  def encrypt(value: String): F[String]
  def decrypt(value: String): F[String]
}
