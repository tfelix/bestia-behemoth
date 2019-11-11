package net.bestia.zoneserver.actor.entity.component

import akka.actor.AbstractActorWithTimers
import akka.actor.ActorRef
import akka.japi.pf.ReceiveBuilder
import mu.KotlinLogging
import net.bestia.model.geometry.Rect
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.actor.entity.RequestComponentMessage
import net.bestia.zoneserver.actor.entity.EntityRequest
import net.bestia.zoneserver.actor.entity.EntityResponse
import net.bestia.zoneserver.actor.entity.SaveAndKillEntity
import net.bestia.zoneserver.actor.AwaitResponseActor
import net.bestia.zoneserver.actor.Responses
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.EntityCollisionService
import net.bestia.zoneserver.entity.component.Component

private val LOG = KotlinLogging.logger { }

// TODO Might better placed at entity actor
data class SubscribeForComponentUpdates(
    val componentType: Class<out Component>,
    val sendUpdateTo: ActorRef
)

interface ComponentBroadcaster<T : Component> {
  fun broadcast(component: T)
}

class ClientInRangeBroadcaster(
    private val entityCollisionService: EntityCollisionService,
    private val range: Rect
) : ComponentBroadcaster<Component> {

  var currentPosition: Vec3 = Vec3.ZERO

  override fun broadcast(component: Component) {
    val rangeOnPos = range.moveTo(currentPosition)
    val entityIds = entityCollisionService.getAllCollidingEntityIds(rangeOnPos)
    // TODO Check if this IDs are player? are they logged in? send them the update
  }
}

abstract class ComponentActor<T : Component>(
    component: T
) : AbstractActorWithTimers() {

  private val componentUpdateSubscriber = mutableSetOf<ActorRef>()

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
        .match(RequestComponentMessage::class.java, this::sendComponent)
        .match(SaveAndKillEntity::class.java) { onSave() }
        .match(component.javaClass) { component = it }

    return builder.build()
  }

  private fun subscribeForComponentUpdate(msg: SubscribeForComponentUpdates) {
    // Check if this message is ment for this actor.
    if (msg.componentType != component::class.java) {
      return
    }
    componentUpdateSubscriber.add(msg.sendUpdateTo)
  }

  private fun notifyComponentChanged(oldComponent: T, newComponent: T) {
    if (oldComponent == newComponent) {
      return
    }

    onComponentChanged(oldComponent, newComponent)
    componentUpdateSubscriber.forEach { it.tell(newComponent, self) }
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

  protected fun announceComponentChange() {
    LOG.warn { "announceComponentChange(): Not implemented." }
  }

  protected open fun createReceive(builder: ReceiveBuilder) {}
}