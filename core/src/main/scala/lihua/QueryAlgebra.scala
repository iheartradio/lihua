package lihua

import java.time.{LocalDate, OffsetDateTime}

sealed trait QueryAlgebra extends Serializable with Product

object QueryAlgebra {
  type FieldName = Symbol
  
  case class And(left: QueryAlgebra, right: QueryAlgebra) extends QueryAlgebra
  case class Or(left: QueryAlgebra, right: QueryAlgebra) extends QueryAlgebra
  case class In(field: FieldName, values: List[FieldValue]) extends QueryAlgebra
  case class Eq(field: FieldName, value: FieldValue) extends QueryAlgebra
  case class GT(field: FieldName, value: FieldValue) extends QueryAlgebra
  case class LT(field: FieldName, value: FieldValue) extends QueryAlgebra
  case class LE(field: FieldName, value: FieldValue) extends QueryAlgebra
  case class GE(field: FieldName, value: FieldValue) extends QueryAlgebra

  sealed trait FieldValue extends Serializable with Product

  object FieldValue {
    case class LongFV(value: Long) extends FieldValue
    case class DoubleFV(value: Double) extends FieldValue
    case class StringFV(value: String) extends FieldValue
    case class BooleanFV(value: Boolean) extends FieldValue
    case class DateFV(value: LocalDate) extends FieldValue
    case class OffsetDateTimeFV(value: OffsetDateTime) extends FieldValue
  }
  
}



