/*
* Copyright [2017] [iHeartMedia Inc]
* All rights reserved
*/
package lihua
package mongo

import play.api.libs.json.{Format, JsObject, Json, Writes}
import reactivemongo.api.{QueryOpts, ReadPreference}


case class Query (
  selector:       JsObject,
  hint:           Option[JsObject]       = None,
  sort:           Option[JsObject]       = None,
  opts:           Option[QueryOpts]      = None,
  readPreference: Option[ReadPreference] = None
)

object Query {
  val idFieldName = "_id"

  def idSelector(id: ObjectId): JsObject = Json.obj(idFieldName -> id.value)

  implicit def fromSelector(selector: JsObject): Query = Query(selector)

  implicit def fromField1[A : Writes](tp: (Symbol, A)): Query =
    Query(Json.obj(tp._1.name -> Json.toJson(tp._2)))

  implicit def fromFields2[A : Writes, B: Writes](p: ((Symbol, A), (Symbol, B))): Query = p match {
    case ((s1, a), (s2, b)) => Query(Json.obj(s1.name -> a, s2.name -> b))
  }

  implicit def fromFields3[A : Writes, B: Writes, C: Writes](p: ((Symbol, A), (Symbol, B), (Symbol, C))): Query = p match {
    case ((s1, a), (s2, b), (s3, c)) => Query(Json.obj(s1.name -> a, s2.name -> b, s3.name -> c))
  }

  implicit def fromFields4[A : Writes, B: Writes, C: Writes, D: Writes](p: ((Symbol, A), (Symbol, B), (Symbol, C), (Symbol, D))): Query = p match {
    case ((s1, a), (s2, b), (s3, c), (s4, d)) => Query(Json.obj(s1.name -> a, s2.name -> b, s3.name -> c, s4.name -> d))
  }

  implicit def fromId(id: ObjectId): Query = idSelector(id)

  implicit def fromIds(ids: List[ObjectId]): Query =
    Json.obj(idFieldName -> Json.obj("$in" -> ids.map(_.value)))

  def byProperty[PT: Format](propertyName: String, propertyValue: PT) =
    Query(Json.obj(propertyName -> propertyValue))
}
