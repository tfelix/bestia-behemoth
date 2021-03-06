package net.bestia.zoneserver.actor.entity

import akka.actor.*
import com.google.common.collect.HashBiMap
import mu.KotlinLogging
import net.bestia.messages.entity.EntityMessage
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.AwaitResponseActor
import net.bestia.zoneserver.actor.entity.component.ComponentMessage
import net.bestia.zoneserver.actor.entity.component.ComponentRequest
import net.bestia.zoneserver.actor.entity.component.EntityComponentActorFactory
import net.bestia.zoneserver.actor.entity.component.UpdateComponent
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.Component

private val LOG = KotlinLogging.logger { }

/**
 * When send to the [EntityActor] it will update the given actor ref if a component
 * has changed.
 */
data class SubscribeForComponentUpdates(
    val componentType: Class<out Component>,
    val sendUpdateTo: ActorRef
)

/**
 * Used to notify all subscribers about a changed component.
 */
data class ComponentUpdated<T : Component>(
    val component: T
)

/**
 * Deletes a component from the Entity.
 */
data class DeleteComponent<T : Component>(
    override val entityId: Long,
    val componentClass: Class<T>
) : EntityMessage

data class NewEntity(
    val entity: Entity
) : EntityMessage {
  override val entityId: Long
    get() = entity.id
}

/**
 * Entity actor is stopped and all data also removed from database. This effectivly
 * deletes the entity.
 */
data class KillEntity(override val entityId: Long) : EntityMessage

/**
 * Entity actor is stopped but it is saved to the database before.
 */
data class SaveAndKillEntity(override val entityId: Long) : EntityMessage

/**
 * The [EntityActor] is a persistent actor managing all aspects of a
 * spawned entity. This means it will keep references to AI actors or attached
 * component actors. This actor has to be used as an persisted shared actor.
 *
 *
 * If there are no more active component actors this actor will cease operation.
 * However incoming component messages will restart this actor and attach the
 * component actor once more.
 *
 *
 * This actor has to react on certain incoming request messages like for example
 * attaching a ticking script to the entity.
 *
 * @author Thomas Felix
 */
@Actor
class EntityActor(
    private val factory: EntityComponentActorFactory
) : AbstractActor() {

  private val uninitialized: Receive = receiveBuilder()
      .match(NewEntity::class.java, this::setupEntity)
      .match(EntityRequest::class.java, this::handleUninitializedEntityRequest)
      .matchAny { context.stop(self) }
      .build()

  private val initialized: Receive = receiveBuilder()
      .match(NewEntity::class.java, this::setupEntity)
      .match(DeleteComponent::class.java, this::removeComponentActor)
      .match(UpdateComponent::class.java, this::updateComponentActor)
      .match(ComponentMessage::class.java, this::onComponentMessage)
      .match(SubscribeForComponentUpdates::class.java, this::subscribeForComponentUpdates)

      .match(Terminated::class.java, this::handleTerminatedComponentActor)
      .match(SaveAndKillEntity::class.java, this::handleSaveAndKill)
      .match(KillEntity::class.java) { context.stop(self) }
      .match(EntityRequest::class.java, this::handleExternalEntityRequest)
      .match(LocalEntityRequest::class.java, this::handleLocalEntityRequest)
      .build()

  private class ComponentActorCache {
    private val classToActor = HashBiMap.create<Class<Component>, ActorRef>()
    val activeComponentActorCount: Int get() = classToActor.size

    fun add(component: Component, compActor: ActorRef) {
      classToActor[component.javaClass] = compActor
    }

    fun allActors(): List<ActorRef> {
      return classToActor.values.toList()
    }

    fun remove(actor: ActorRef) {
      classToActor.inverse().remove(actor)
    }

    fun get(componentClass: Class<out Component>): ActorRef? {
      return classToActor[componentClass]
    }

    fun getAllCachedComponentClasses(): Set<Class<Component>> {
      return classToActor.keys.toSet()
    }
  }

  private val componentUpdateSubscriberCache = mutableSetOf<SubscribeForComponentUpdates>()
  private val componentActorCache = ComponentActorCache()
  private var entityId: Long = 0

  override fun createReceive(): Receive {
    return uninitialized
  }

  /**
   * We must cache any component subscription message as the actors are usually not re-sending it again
   * and the component actor might not be there already. We need to re-send it to the component actor if
   * an actor shows up.
   */
  private fun subscribeForComponentUpdates(msg: SubscribeForComponentUpdates) {
    componentUpdateSubscriberCache.add(msg)
  }

  private fun onComponentMessage(msg: ComponentMessage<*>) {
    val componentActor = componentActorCache.get(msg.componentType)

    if (componentActor == null) {
      LOG.debug { "Component missing: ${msg.componentType}" }
      return
    }

    componentActor.tell(msg, self)
  }

  private fun setupEntity(msg: NewEntity) {
    LOG.trace { "Creating entity actor: $msg" }
    entityId = msg.entity.id
    msg.entity.allComponents.forEach { createComponentActor(it) }

    context.become(initialized)
  }

  private fun handleUninitializedEntityRequest(msg: EntityRequest) {
    msg.replyTo.tell(EntityDoesNotExist, self)
    context.stop(self)
  }

  private fun handleLocalEntityRequest(msg: LocalEntityRequest) {
    handleEntityRequest(msg.replyTo, msg.context)
  }

  private fun handleExternalEntityRequest(msg: EntityRequest) {
    handleEntityRequest(msg.replyTo, msg.context)
  }

  private fun handleEntityRequest(requester: ActorRef, requestCtx: Any?) {
    val awaitedComponentClasses = componentActorCache.getAllCachedComponentClasses()

    val hasAllResponses = { receivedResponses: List<Any> ->
      receivedResponses.map { it.javaClass }
          .toSet() == awaitedComponentClasses
    }

    val waitResponseProps = AwaitResponseActor.props(hasAllResponses) {
      @Suppress("UNCHECKED_CAST")
      val entity = Entity.withComponents(
          entityId,
          it.getAllResponses()
      )
      val entityResponse = EntityResponse(
          entity,
          requestCtx
      )
      requester.tell(entityResponse, self)
    }
    val waitResponseActor = context.actorOf(waitResponseProps)

    componentActorCache.allActors().forEach {
      val requestMsg = ComponentRequest(replyTo = waitResponseActor)
      it.tell(requestMsg, self)
    }
  }

  private fun updateComponentActor(msg: UpdateComponent<*>) {
    val componentActor = componentActorCache.get(msg.component.javaClass)
        ?: createComponentActor(msg.component)
        ?: return

    LOG.trace { "Updating component: $msg on entity: $entityId" }
    componentActor.tell(msg.component, sender())
  }

  private fun removeComponentActor(msg: DeleteComponent<*>) {
    LOG.trace { "Removing component: ${msg.componentClass.simpleName} on entity: $entityId" }
    componentActorCache.get(msg.componentClass)?.tell(PoisonPill.getInstance(), self)
  }

  private fun createComponentActor(component: Component): ActorRef? {
    val existingCompActor = componentActorCache.get(component.javaClass)

    if (existingCompActor != null) {
      return existingCompActor
    }

    return factory.startActor(context, component)?.also {
      context().watch(it)
      componentActorCache.add(component, it)
      componentUpdateSubscriberCache.forEach { subMsg -> it.tell(subMsg, self) }
    }
  }

  /**
   * Terminate itself when there is no active component anymore.
   */
  private fun handleTerminatedComponentActor(term: Terminated) {
    componentActorCache.remove(term.actor)
    if (componentActorCache.activeComponentActorCount == 0) {
      context.stop(self)
    }
  }

  private fun handleSaveAndKill(msg: SaveAndKillEntity) {
    // TODO Persist the given entity.
    componentActorCache.allActors().forEach {
      // Make own message
      it.tell(msg, sender)
    }

    context.stop(self)
  }
}
