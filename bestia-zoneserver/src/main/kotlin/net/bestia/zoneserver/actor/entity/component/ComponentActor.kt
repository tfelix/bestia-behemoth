package net.bestia.zoneserver.actor.entity.component

import akka.actor.AbstractActorWithTimers
import akka.japi.pf.ReceiveBuilder
import mu.KotlinLogging
import net.bestia.zoneserver.actor.entity.RequestComponentMessage
import net.bestia.zoneserver.actor.entity.EntityRequest
import net.bestia.zoneserver.actor.entity.EntityResponse
import net.bestia.zoneserver.actor.entity.SaveAndKillEntity
import net.bestia.zoneserver.actor.AwaitResponseActor
import net.bestia.zoneserver.actor.Responses
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.Component

private val LOG = KotlinLogging.logger { }

abstract class ComponentActor<T : Component>(
    component: T
) : AbstractActorWithTimers() {

  protected fun fetchEntity(
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
      handleComponentSet(value)
      field = value
    }

  final override fun createReceive(): Receive {
    val builder = receiveBuilder()
    createReceive(builder)

    builder
        .match(RequestComponentMessage::class.java, this::sendComponent)
        .match(SaveAndKillEntity::class.java) { onSave() }
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
  }

  private fun sendComponent(msg: RequestComponentMessage) {
    msg.replyTo.tell(component, self)
  }

  protected open fun onComponentChanged(oldComponent: T, newComponent: T) {}

  /**
   * Actor should save the component to a persistent storage because the actor is getting killed
   * most likely soon.
   */
  protected open fun onSave() {}

  protected fun announceComponentChange() {}

  protected open fun createReceive(builder: ReceiveBuilder) {}
}