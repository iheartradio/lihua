/*
* Copyright [2017] [iHeartMedia Inc]
* All rights reserved
*/
package lihua.mongo

import org.joda.time.DateTime
import play.api.libs.json._

import scala.reflect.ClassTag

object JsonFormats {

  implicit object JodaFormat extends Format[DateTime] {

    override def reads(json: JsValue): JsResult[DateTime] = (json \ "$date").validate[Long].map(new DateTime(_))

    override def writes(o: DateTime): JsValue = Json.obj("$date" → o.getMillis)
  }

  object StringBooleanFormat extends Format[Boolean] {

    override def reads(json: JsValue): JsResult[Boolean] = json.validate[String].map(_.toLowerCase == "true")

    override def writes(o: Boolean): JsValue = JsString(o.toString)
  }

  object IntBooleanFormat extends Format[Boolean] {

    override def reads(json: JsValue): JsResult[Boolean] = json.validate[Int].map(_ != 0)

    override def writes(o: Boolean): JsValue = JsNumber(if (o) 1 else 0)
  }

  implicit class JsPathMongoDBOps(val self: JsPath) extends AnyVal {
    def formatObjectId = OFormat[String](self.read[ObjectId].map(_.value), OWrites[String] { s ⇒ self.write[ObjectId].writes(ObjectId(s)) })
  }

  implicit def mapFormat[KT: StringParser, VT: Format]: Format[Map[KT, VT]] = new Format[Map[KT, VT]] {
    def writes(o: Map[KT, VT]): JsValue =
      JsObject(o.toSeq.map { case (k, v) ⇒ (k.toString, Json.toJson(v)) })

    def reads(json: JsValue): JsResult[Map[KT, VT]] =
      json.validate[JsObject].map(_.fields.map {
        case (ks, vValue) ⇒ (implicitly[StringParser[KT]].parse(ks), vValue.as[VT])
      }.toMap)
  }

  private def myClassOf[T: ClassTag] = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]

  implicit def enumWrites[ET <: Enum[ET]]: Writes[ET] = Writes {
    case r: Enum[_] ⇒ JsString(r.name())
  }

  implicit def enumReads[ET <: Enum[ET]: ClassTag]: Reads[ET] = Reads {
    case JsString(name) ⇒
      JsSuccess(Enum.valueOf(myClassOf[ET], name))
    //TODO: improve error
    case _ ⇒ JsError("unrecognized format")
  }

  implicit def enumFormats[ET <: Enum[ET]: ClassTag]: Format[ET] = Format(enumReads[ET], enumWrites[ET])

  trait StringParser[T] {
    def parse(s: String): T
  }

  object StringParser {
    implicit val intStringParser: StringParser[Int] = new StringParser[Int] {
      def parse(s: String): Int = java.lang.Integer.parseInt(s)
    }

    implicit val stringStringParser: StringParser[String] = new StringParser[String] {
      def parse(s: String): String = s
    }
  }

}
