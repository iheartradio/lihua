package lihua

final case class ObjectId(value: String) extends AnyVal

object ObjectId {
  implicit def toObjectId(value: String) = ObjectId(value)
  implicit def toString(oid: ObjectId) = oid.value


}
