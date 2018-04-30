package lihua.mongo

import cats.data.NonEmptyList

sealed trait DBError extends Throwable with Product with Serializable

object DBError {

  case object NotFound extends DBError

  case class DBException(throwable: Throwable, extraMsg: String = "") extends DBError {
    override def getCause: Throwable = throwable.getCause

    override def getMessage: String =  throwable.getMessage + " " + extraMsg
  }

  case class WriteError(details: NonEmptyList[WriteErrorDetail]) extends DBError {
    override def getMessage: String =  details.toString()
  }

  sealed trait WriteErrorDetail extends Product with Serializable {
    def code: Int
    def msg: String
    override def toString: String = s"code: $code, message: $msg"
  }

  case class ItemWriteErrorDetail(code: Int, msg: String) extends WriteErrorDetail
  case object UpdatedCountErrorDetail extends WriteErrorDetail {
    val code = 0
    val msg = "updated count is 0, nothing gets updated"
  }
  case class WriteConcernErrorDetail(code: Int, msg: String) extends WriteErrorDetail

}
