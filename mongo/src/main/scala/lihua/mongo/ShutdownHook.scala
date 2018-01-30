/*
* Copyright [2017] [iHeartMedia Inc]
* All rights reserved
*/
package lihua.mongo

trait ShutdownHook {
  def onShutdown[T](code: ⇒ T): Unit
}


object ShutdownHook {
  object jvmShutdownHook extends ShutdownHook {
    def onShutdown[T](code: ⇒ T): Unit = {
      sys.addShutdownHook({code; ()})
      ()
    }
  }
}
