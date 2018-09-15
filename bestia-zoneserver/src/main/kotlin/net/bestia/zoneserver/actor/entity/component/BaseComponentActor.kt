package net.bestia.zoneserver.actor.entity.component

import akka.actor.AbstractActor
import akka.japi.pf.ReceiveBuilder
import net.bestia.entity.component.Component

abstract class BaseComponentActor<T : Component>(
        private var component: T
) : AbstractActor() {

  override fun createReceive(): AbstractActor.Receive {
    val builder = receiveBuilder()
    createReceive(builder)

    builder.match(RequestComponentMessage::class.java, this::sendComponent)

    return builder.build()
  }

  private fun sendComponent(msg: RequestComponentMessage) {
    msg.requester.tell(ResponseComponentMessage(component), self)
  }

  protected abstract fun createReceive(builder: ReceiveBuilder)

  /**
   * Depending of the component it will check which entities of connected clients need to
   * be notified about the change.
   */
  protected fun updateEntitiesAndClients() {

  }
}