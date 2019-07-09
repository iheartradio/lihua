package lihua


object EntityId {
  private[lihua] type Base
  private[lihua] trait Tag extends Any
  type Type <: Base with Tag

  def apply(v: String): EntityId = v.asInstanceOf[EntityId]

  def unwrap(e: EntityId): String = e.asInstanceOf[String]

  def unapply(arg: EntityId): Option[String] = Some(unwrap(arg))

  implicit def toOps(eid: EntityId): EntityIdOps = new EntityIdOps(eid)
}


class EntityIdOps(private val eid: EntityId) extends AnyVal {
  def value: String = EntityId.unwrap(eid)
}