package net.bestia.zoneserver.actor.entity.component

import akka.actor.AbstractActor
import akka.japi.pf.ReceiveBuilder
import mu.KotlinLogging
import net.bestia.messages.entity.RequestComponentMessage
import net.bestia.messages.entity.EntityRequest
import net.bestia.messages.entity.EntityResponse
import net.bestia.messages.entity.SaveAndKillEntity
import net.bestia.zoneserver.actor.AwaitResponseActor
import net.bestia.zoneserver.actor.EntitiesResponse
import net.bestia.zoneserver.actor.Responses
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.Component

private val LOG = KotlinLogging.logger { }

abstract class ComponentActor<T : Component>(
    component: T
) : AbstractActor() {

  protected fun awaitEntity(
      callback: (Entity) -> Unit
  ) {
    val hasReceived: (List<Any>) -> Boolean = {
      true
    }
    val transformResponse = { response: Responses ->
      val entityResponse = response.getResponse(EntityResponse::class)
      callback(entityResponse.entity)
    }
    val props = AwaitResponseActor.props(hasReceived, transformResponse)
    val requestActor = context.actorOf(props)
    val requestMsg = EntityRequest(requestActor)
    context.parent.tell(requestMsg, requestActor)
  }

  protected var component = component
    set(value) {
      LOG.trace { "Set component $component on ${this.self.path()}" }
      field = value
    }

  override fun createReceive(): AbstractActor.Receive {
    val builder = receiveBuilder()
    createReceive(builder)

    builder
        .match(RequestComponentMessage::class.java, this::sendComponent)
        .match(SaveAndKillEntity::class.java) { _ -> onSave() }
        .match(component.javaClass, this::handleComponentSet)

    return builder.build()
  }

  private fun handleComponentSet(newComponent: T) {
    if (component == newComponent) {
      return
    }

    val oldComponent = component
    component = newComponent
    onComponentChanged(oldComponent, component)
    updateEntitiesAboutComponentChanged()
  }

  private fun sendComponent(msg: RequestComponentMessage) {
    msg.requester.tell(component, self)
  }

  protected open fun onComponentChanged(oldComponent: T, newComponent: T) {}

  /**
   * Actor should save the component to a persistent storage because the actor is getting killed
   * most likely soon.
   */
  protected open fun onSave() {}

  protected abstract fun createReceive(builder: ReceiveBuilder)

  /**
   * Depending of the component it will check which entities of connected clients need to
   * be notified about the change.
   */
  protected fun updateEntitiesAboutComponentChanged() {}
}