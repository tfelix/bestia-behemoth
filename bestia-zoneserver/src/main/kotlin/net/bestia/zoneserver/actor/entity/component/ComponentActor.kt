package net.bestia.zoneserver.actor.entity.component

import akka.actor.ActorRef
import akka.japi.pf.ReceiveBuilder
import mu.KotlinLogging
import net.bestia.messages.entity.EntityMessage
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.entity.DeleteComponent
import net.bestia.zoneserver.actor.entity.EntityRequestingActor
import net.bestia.zoneserver.actor.entity.SaveAndKillEntity
import net.bestia.zoneserver.actor.entity.SubscribeForComponentUpdates
import net.bestia.zoneserver.actor.entity.transmit.TransmitRequest
import net.bestia.zoneserver.actor.entity.transmit.ClientComponentTransmitActor
import net.bestia.zoneserver.entity.component.Component

private val LOG = KotlinLogging.logger { }

data class UpdateComponent<T : Component>(
    val component: T
) : EntityMessage, ComponentMessage<T> {
  override val entityId: Long
    get() = component.entityId

  override val componentType: Class<out T>
    get() = component.javaClass
}

abstract class ComponentActor<T : Component>(
    component: T
) : EntityRequestingActor() {

  private val updateComponentSubscriber = mutableSetOf<ActorRef>()
  private val broadcastToClients = SpringExtension.actorOf(context, ClientComponentTransmitActor::class.java)

  protected var component = component
    set(value) {
      LOG.trace { "Set component $component on ${this.self.path()}" }
      val oldComponent = field
      field = value
      notifyComponentChanged(oldComponent, value)
    }

  protected val entityId: Long get() = component.entityId

  final override fun createReceive(): Receive {
    val builder = receiveBuilder()
    createReceive(builder)

    builder
        .match(SubscribeForComponentUpdates::class.java, this::subscribeForComponentUpdate)
        .match(ComponentRequest::class.java, this::sendComponent)
        .match(SaveAndKillEntity::class.java) { onSave() }
        .match(component.javaClass) { component = it }

    return builder.build()
  }

  override fun preStart() {
    // initial notification about the new component set
    onComponentChanged(component, component)
    updateComponentSubscriber.forEach { it.tell(component, self) }
    updateConnectedClients(component)
  }

  /**
   * The actor registers to get notified if a certain component gets updated on the entity. This is usefull to calculate
   * dependent properties e.g. if the level components has changes you probably want to update the status value component
   * with the new values.
   */
  protected fun createComponentUpdateSubscription(clazz: Class<out Component>) {
    val subscribeForComponentUpdates = SubscribeForComponentUpdates(clazz, self)
    context.parent.tell(subscribeForComponentUpdates, self)
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

    LOG.trace { "Updating onComponentChanged subscriber: $updateComponentSubscriber" }

    onComponentChanged(oldComponent, newComponent)
    updateComponentSubscriber.forEach { it.tell(newComponent, self) }
    updateConnectedClients(newComponent)
  }

  private fun sendComponent(msg: ComponentRequest) {
    msg.replyTo.tell(component, self)
  }

  protected open fun onComponentChanged(oldComponent: T, newComponent: T) {}

  /**
   * Deletes this component from the entity. The actor is then stopped by the parent.
   */
  protected fun deleteSelf() {
    LOG.trace { "DeleteSelf component ${component.javaClass.simpleName}" }
    context.parent.tell(DeleteComponent(component.entityId, component::class.java), self)
  }

  /**
   * Deletes this component from the entity. The actor is then stopped by the parent.
   */
  protected fun deleteComponentFromEntity(componentClass: Class<out Component>) {
    context.parent.tell(DeleteComponent(component.entityId, componentClass), self)
  }

  /**
   * Actor should save the component to a persistent storage because the actor is getting killed
   * most likely soon.
   */
  protected open fun onSave() {}

  private fun updateConnectedClients(newComponent: T) {
    requestOwnerEntity {
      val transmitRequest = TransmitRequest(newComponent, it)
      broadcastToClients.tell(transmitRequest, self)
    }
  }

  protected open fun createReceive(builder: ReceiveBuilder) {}
}