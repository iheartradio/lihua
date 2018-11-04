package lihua

import reactivemongo.bson.BSONObjectID

package object mongo {
  def generateId: ObjectId = BSONObjectID.generate.stringify
}
