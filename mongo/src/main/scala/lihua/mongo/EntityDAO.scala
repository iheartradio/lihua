/*
* Copyright [2017] [iHeartMedia Inc]
* All rights reserved
*/
package lihua.mongo

import lihua.mongo.EntityDAO.Query
import play.api.libs.json.{Format, JsObject, Json, Writes}
import reactivemongo.api.{QueryOpts, ReadPreference}

import scala.concurrent.duration.FiniteDuration
import mainecoon.{autoFunctorK, finalAlg}

/**
 * Final tagless encoding of the DAO Algebra
 * @tparam F effect Monad
 * @tparam T type of the domain model
 */
@autoFunctorK(autoDerivation = true) @finalAlg
trait EntityDAO[F[_], T] {

  def get(id: ObjectId): F[Entity[T]]

  def insert(t: T): F[Entity[T]]

  def update(entity: Entity[T]): F[Entity[T]]

  def invalidateCache(query: Query): F[Unit]

  def find(query: Query): F[Vector[Entity[T]]]

  def findOne(query: Query): F[Entity[T]]

  def findCached(query: Query, ttl: FiniteDuration): F[Vector[Entity[T]]]

  def remove(id: ObjectId): F[Unit]

  def removeAll(selector: JsObject): F[Int]

}

object EntityDAO {
  case class Query (
    selector:       JsObject,
    hint:           Option[JsObject]       = None,
    sort:           Option[JsObject]       = None,
    opts:           Option[QueryOpts]      = None, //todo: abstract out this hard dependency on reactive Mongo
    readPreference: Option[ReadPreference] = None
  )

  object Query {
    val idFieldName = "_id"

    def idSelector(id: ObjectId): JsObject = Json.obj(idFieldName -> id)

    implicit def fromSelector(selector: JsObject): Query = Query(selector)

    implicit def fromTuples[A : Writes](tps: (Symbol, A)*): Query = Query(JsObject(tps.map(p => (p._1.toString, Json.toJson(p._2)))))

    implicit def fromId(id: ObjectId): Query = idSelector(id)

    implicit def fromIds(ids: List[ObjectId]): Query = Json.obj(idFieldName -> Json.obj("$in" -> ids))

    def byProperty[PT: Format](propertyName: String, propertyValue: PT) =
      Query(Json.obj(propertyName -> propertyValue))
  }
}
