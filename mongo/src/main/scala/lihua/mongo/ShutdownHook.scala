/*
* Copyright [2017] [iHeartMedia Inc]
* All rights reserved
*/
package lihua.mongo

trait ShutdownHook {
  def onShutdown[T](code: ⇒ T): Unit
}

