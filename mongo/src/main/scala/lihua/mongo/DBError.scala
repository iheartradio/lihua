package lihua.mongo

import cats.data.NonEmptyList

import scala.util.control.NoStackTrace



sealed trait DBError extends Throwable with Product with Serializable with NoStackTrace

object DBError {

  case object NotFound extends DBError

  case class DBException(throwable: Throwable) extends DBError

  case class WriteError(details: NonEmptyList[WriteErrorDetail]) extends DBError

  sealed trait WriteErrorDetail extends Product with Serializable {
    def code: Int
    def msg: String
  }

  case class ItemWriteErrorDetail(code: Int, msg: String) extends WriteErrorDetail
  case class WriteConcernErrorDetail(code: Int, msg: String) extends WriteErrorDetail

}
