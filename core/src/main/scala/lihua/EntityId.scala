package lihua

final case class EntityId(value: String) extends AnyVal

object EntityId {
  implicit def toEntityId(value: String) = EntityId(value)
  implicit def toString(oid: EntityId) = oid.value


}
