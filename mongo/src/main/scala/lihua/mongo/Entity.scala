/*
* Copyright [2017] [iHeartMedia Inc]
* All rights reserved
*/
package lihua.mongo
import play.api.libs.json._
import Json.toJson

case class Entity[T](_id: ObjectId, data: T)

object Entity {
  implicit def entityFormat[T: Format]: OFormat[Entity[T]] = new OFormat[Entity[T]] {
    def writes(e: Entity[T]): JsObject =
      toJson(e.data).as[JsObject] + ("_id" -> toJson(e._id))

    def reads(json: JsValue): JsResult[Entity[T]] = for {
      id <- (json \ "_id").validate[ObjectId]
      t <- json.validate[T]
    } yield Entity(id, t)
  }
}
