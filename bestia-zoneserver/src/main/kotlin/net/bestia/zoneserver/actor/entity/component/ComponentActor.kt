package net.bestia.zoneserver.actor.entity.component

import akka.actor.AbstractActor
import akka.japi.pf.ReceiveBuilder
import mu.KotlinLogging
import net.bestia.messages.entity.RequestComponentMessage
import net.bestia.zoneserver.entity.component.Component

private val LOG = KotlinLogging.logger{ }

abstract class ComponentActor<T : Component>(
        component: T
) : AbstractActor() {

  protected var component = component
    set(value) {
      LOG.trace { "Sets component $component on ${this.self.path()}" }
      field = value
    }

  override fun createReceive(): AbstractActor.Receive {
    val builder = receiveBuilder()
    createReceive(builder)

    builder.match(RequestComponentMessage::class.java, this::sendComponent)

    return builder.build()
  }

  private fun sendComponent(msg: RequestComponentMessage) {
    msg.requester.tell(component, self)
  }

  protected abstract fun createReceive(builder: ReceiveBuilder)

  /**
   * Depending of the component it will check which entities of connected clients need to
   * be notified about the change.
   */
  protected fun updateEntitiesAndClients() {

  }
}