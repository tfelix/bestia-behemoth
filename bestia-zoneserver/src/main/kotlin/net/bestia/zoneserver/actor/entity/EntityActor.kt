package net.bestia.zoneserver.actor.entity

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Terminated
import com.google.common.collect.HashBiMap
import mu.KotlinLogging
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.AwaitResponseActor
import net.bestia.zoneserver.actor.entity.component.EntityComponentActorFactory
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.Component as BestiaComponent

private val LOG = KotlinLogging.logger { }

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
    private val factory: EntityComponentActorFactory,
    private val messageApi: MessageApi
) : AbstractActor() {

  private class ComponentActorCache {
    private val classToActor = HashBiMap.create<Class<BestiaComponent>, ActorRef>()
    val activeComponentActorCount: Int get() = classToActor.size

    fun add(component: BestiaComponent, compActor: ActorRef) {
      classToActor[component.javaClass] = compActor
    }

    fun allActors(): List<ActorRef> {
      return classToActor.values.toList()
    }

    fun remove(actor: ActorRef) {
      classToActor.inverse().remove(actor)
    }

    fun get(componentClass: Class<out BestiaComponent>): ActorRef? {
      return classToActor[componentClass]
    }

    fun getAllCachedComponentClasses(): Set<Class<BestiaComponent>> {
      return classToActor.keys.toSet()
    }
  }

  private val componentActorCache = ComponentActorCache()
  private var entityId: Long = 0

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
        .match(AddComponentMessage::class.java, this::addComponentActor)
        .match(DeleteComponentMessage::class.java, this::removeComponentActor)
        .match(UpdateComponentMessage::class.java, this::updateComponentActor)

        .match(Terminated::class.java, this::handleTerminated)
        .match(SaveAndKillEntity::class.java, this::handleSaveAndKill)
        .match(EntityRequest::class.java, this::handleEntityRequest)
        .matchAny(this::terminateIfNoSuitableMessage)
        .build()
  }

  private fun handleEntityRequest(msg: EntityRequest) {
    if (entityId == 0L) {
      LOG.debug { "${msg.replyTo} requested not existing entity" }
      msg.replyTo.tell(EntityDoesNotExist, self)
    }

    val awaitedComponentClasses = componentActorCache.getAllCachedComponentClasses()

    val hasAllResponses = { receivedResponses: List<Any> ->
      receivedResponses.asSequence()
          .map { it.javaClass }
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
          msg.context
      )
      msg.replyTo.tell(entityResponse, self)
    }
    val waitResponseActor = context.actorOf(waitResponseProps)

    componentActorCache.allActors().forEach {
      val requestMsg = RequestComponentMessage(replyTo = waitResponseActor)
      it.tell(requestMsg, self)
    }
  }

  private fun terminateIfNoSuitableMessage(msg: Any) {
    if (entityId == 0L && msg !is AddComponentMessage<*>) {
      LOG.info { "Uninitialized EntityActor received message: $msg but awaited Component. Terminate." }
      context.stop(self)
    }

    unhandled(msg)
  }

  private fun updateComponentActor(msg: UpdateComponentMessage<*>) {
    if (entityId == 0L) {
      entityId = msg.component.entityId
    }

    val componentActor = componentActorCache.get(msg.component.javaClass)
        ?: createComponentActor(msg.component)
        ?: return

    LOG.debug { "Updating component: $msg on entity: $entityId." }
    componentActor.tell(msg.component, sender())
  }

  private fun addComponentActor(msg: AddComponentMessage<*>) {
    LOG.debug { "Installing component: ${msg.component::class.java.simpleName} on entity: $entityId." }
    createComponentActor(msg.component)
  }

  private fun removeComponentActor(msg: DeleteComponentMessage<*>) {
    LOG.debug { "Removing component: ${msg.componentClass.simpleName} on entity: $entityId" }
    componentActorCache.get(msg.componentClass)?.tell(PoisonPill.getInstance(), self)
  }

  private fun createComponentActor(component: BestiaComponent): ActorRef? {
    return factory.startActor(context, component)?.also {
      context().watch(it)
      componentActorCache.add(component, it)
    }
  }

  /**
   * Terminate itself when there is no active component anymore.
   */
  private fun handleTerminated(term: Terminated) {
    componentActorCache.remove(term.actor)
    if (componentActorCache.activeComponentActorCount == 0) {
      context.stop(self)
    }
  }

  private fun handleSaveAndKill(msg: SaveAndKillEntity) {
    awaitEntityResponse(messageApi, context, entityId) { entity ->
      // TODO Persist the given entity.
      LOG.error { "Persisting $entity entities/components is not yet implemented." }

      componentActorCache.allActors().forEach {
        it.tell(msg, sender)
      }

      context.stop(self)
    }
  }
}
