package lihua

import reactivemongo.bson.BSONObjectID

package object mongo {
  def generateId: EntityId = BSONObjectID.generate.stringify
}
