package lihua

object `package` {

  type EntityId = EntityId.Type

  implicit def toDataOps[A](a: A): DataOps[A] = new DataOps(a)
}

private[lihua] class DataOps[A](private val a: A) extends AnyVal {
  def toEntity(id: String): Entity[A] = toEntity(EntityId(id))
  def toEntity(entityId: EntityId): Entity[A] = Entity(entityId, a)
}
