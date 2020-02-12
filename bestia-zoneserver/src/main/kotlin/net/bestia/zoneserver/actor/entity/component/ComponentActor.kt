package net.bestia.zoneserver.actor.entity.component

import akka.actor.AbstractActorWithTimers
import akka.actor.ActorRef
import akka.japi.pf.ReceiveBuilder
import mu.KotlinLogging
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.actor.AwaitResponseActor
import net.bestia.zoneserver.actor.Responses
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.entity.*
import net.bestia.zoneserver.actor.entity.SaveAndKillEntity
import net.bestia.zoneserver.actor.entity.broadcast.BroadcastComponentMessage
import net.bestia.zoneserver.actor.entity.broadcast.ClientComponentBroadcastActor
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.Component

private val LOG = KotlinLogging.logger { }

// TODO Might better placed at entity actor
data class SubscribeForComponentUpdates(
    val componentType: Class<out Component>,
    val sendUpdateTo: ActorRef
)

abstract class ComponentActor<T : Component>(
    component: T
) : AbstractActorWithTimers() {

  private val broadcastToClientsOnChange = javaClass.getAnnotation(ActorComponent::class.java)?.broadcastToClients
      ?: false
  private val updateComponentSubscriber = mutableSetOf<ActorRef>()
  private val broadcastToClients = SpringExtension.actorOf(context, ClientComponentBroadcastActor::class.java)

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
    val props = AwaitResponseActor.props(
        checkResponseReceived = hasReceived,
        action = transformResponse
    )
    val requestActor = context.actorOf(props)
    val requestMsg = EntityRequest(requestActor)
    context.parent.tell(requestMsg, requestActor)
  }

  protected var component = component
    set(value) {
      LOG.trace { "Set component $component on ${this.self.path()}" }
      val oldComponent = field
      field = value
      notifyComponentChanged(oldComponent, value)
    }

  final override fun createReceive(): Receive {
    val builder = receiveBuilder()
    createReceive(builder)

    builder
        .match(SubscribeForComponentUpdates::class.java, this::subscribeForComponentUpdate)
        .match(RequestComponent::class.java, this::sendComponent)
        .match(SaveAndKillEntity::class.java) { onSave() }
        .match(component.javaClass) { component = it }

    return builder.build()
  }

  private fun subscribeForComponentUpdate(msg: SubscribeForComponentUpdates) {
    // Check if this message is ment for this actor.
    if (msg.componentType != component::class.java) {
      return
    }
    updateComponentSubscriber.add(msg.sendUpdateTo)
  }

  private fun notifyComponentChanged(oldComponent: T, newComponent: T) {
    if (oldComponent == newComponent) {
      return
    }

    onComponentChanged(oldComponent, newComponent)
    updateComponentSubscriber.forEach { it.tell(newComponent, self) }
    updateConnectedClients(newComponent)
  }

  private fun sendComponent(msg: RequestComponent) {
    msg.replyTo.tell(component, self)
  }

  protected open fun onComponentChanged(oldComponent: T, newComponent: T) {}

  /**
   * Actor should save the component to a persistent storage because the actor is getting killed
   * most likely soon.
   */
  protected open fun onSave() {}

  private fun updateConnectedClients(newComponent: T) {
    if (!broadcastToClientsOnChange) {
      return
    }

    fetchEntity {
      val msg = BroadcastComponentMessage(newComponent, it)
      broadcastToClients.tell(msg, self)
    }
  }

  protected open fun createReceive(builder: ReceiveBuilder) {}
}