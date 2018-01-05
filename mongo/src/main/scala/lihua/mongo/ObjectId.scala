/*
* Copyright [2017] [iHeartMedia Inc]
* All rights reserved
*/
package lihua.mongo

import play.api.libs.json._
import reactivemongo.bson.BSONObjectID

import scala.language.implicitConversions

final case class ObjectId(value: String) extends AnyVal

object ObjectId {
  implicit def toObjectId(value: String) = ObjectId(value)
  implicit def toString(oid: ObjectId) = oid.value

  def generate: String = BSONObjectID.generate.stringify

  implicit object ObjectIdFormat extends Format[ObjectId] {

    override def reads(json: JsValue): JsResult[ObjectId] = (json \ "$oid").validate[String].map(toObjectId)

    override def writes(o: ObjectId): JsValue = Json.obj("$oid" → o.value)
  }
}
