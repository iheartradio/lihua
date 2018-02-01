/*
* Copyright [2017] [iHeartMedia Inc]
* All rights reserved
*/
package lihua.mongo

trait ShutdownHook {
  def onShutdown[T](code: ⇒ T): Unit
}


object ShutdownHook {
  object manual extends ShutdownHook {
    @volatile
    private var callbacks: List[() => _] = Nil
    override def onShutdown[T](code: => T): Unit = {
      callbacks = (() => code) :: callbacks
    }
    def shutdown(): Unit = callbacks.foreach(_())
  }
}
