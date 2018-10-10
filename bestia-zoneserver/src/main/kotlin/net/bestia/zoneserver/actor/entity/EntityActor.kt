package net.bestia.zoneserver.actor.entity

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Terminated
import com.google.common.collect.HashBiMap
import mu.KotlinLogging
import net.bestia.messages.entity.*
import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.actor.AwaitResponseActor
import net.bestia.zoneserver.actor.awaitEntityResponse
import net.bestia.zoneserver.actor.entity.component.EntityComponentActorFactory
import net.bestia.zoneserver.entity.Entity
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
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
@Component
@Scope("prototype")
class EntityActor(
    private val factory: EntityComponentActorFactory,
    private val messageApi: MessageApi
) : AbstractActor() {

  class ComponentActorCache {

    private val classToActor = HashBiMap.create<Class<BestiaComponent>, ActorRef>()

    val activeComponentActorCount: Int get() = classToActor.size

    fun add(component: BestiaComponent, compActor: ActorRef) {
      classToActor[component.javaClass] = compActor
    }

    fun has(clazz: Class<out BestiaComponent>): Boolean {
      return classToActor.containsKey(clazz)
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
        .match(BestiaComponent::class.java, this::handleComponent)
        .match(ComponentClassEnvelope::class.java, this::handleComponentClassEnvelope)
        .match(ComponentBroadcastEnvelope::class.java, this::handleAllComponentBroadcast)
        .match(ComponentRequestMessage::class.java, this::handleComponentRequest)
        .match(DeleteComponentMessage::class.java, this::handleComponentRemove)

        .match(Terminated::class.java, this::handleTerminated)

        .match(SaveAndKillEntity::class.java, this::handleSaveAndKill)
        .match(EntityRequest::class.java, this::handleEntityRequest)
        .matchAny(this::terminateIfNoSuitableMessage)
        .build()
  }

  private fun handleEntityRequest(msg: EntityRequest) {
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
          it.getAllResponses() as List<BestiaComponent>
      )
      val entityResponse = EntityResponse(
          entity,
          msg.context
      )
      msg.requester.tell(entityResponse, self)
    }
    val waitResponseActor = context.actorOf(waitResponseProps)

    componentActorCache.allActors().forEach {
      val requestMsg = RequestComponentMessage(requester = waitResponseActor)
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

  private fun handleComponent(msg: BestiaComponent) {
    if(entityId == 0L) {
      entityId = msg.entityId
    }

    if(!componentActorCache.has(msg.javaClass)) {
      installComponentActor(msg)
    } else {
      LOG.debug { "Updating component: $msg on entity: $entityId." }
      val componentActor = componentActorCache.get(msg.javaClass)
      trySendComponentActor(componentActor, msg)
    }
  }

  private fun installComponentActor(msg: BestiaComponent) {
    LOG.debug { "Installing component: $msg on entity: $entityId." }

    val compActor = factory.startActor(context, msg) ?: run {
      LOG.warn { "Component actor for comp id $msg was not created." }
      return
    }

    context().watch(compActor)
    componentActorCache.add(msg, compActor)
  }

  private fun handleAllComponentBroadcast(msg: ComponentBroadcastEnvelope) {
    LOG.debug { "Entity received $msg" }
    componentActorCache.allActors().forEach { it.tell(msg.content, sender) }
  }

  private fun handleComponentClassEnvelope(msg: ComponentClassEnvelope<*>) {
    val componentActor = componentActorCache.get(msg.componentClass)
    trySendComponentActor(componentActor, msg.content)
  }

  private fun handleComponentRequest(msg: ComponentRequestMessage<*>) {
    val componentActor = componentActorCache.get(msg.componentClass)
    trySendComponentActor(componentActor, msg)
  }

  private fun trySendComponentActor(compActor: ActorRef?, content: Any) {
    when (compActor) {
      null -> {
        LOG.debug("Component message unhandled: {}.", content)
        unhandled(content)
      }
      else -> {
        LOG.debug("Forwarding message: {} to: {}.", content, compActor)
        compActor.tell(content, self)
      }
    }
  }

  private fun handleComponentRemove(msg: DeleteComponentMessage<*>) {
    LOG.debug { "Removing component: ${msg.componentClass.simpleName} on entity: $entityId" }
    componentActorCache.get(msg.componentClass)?.tell(PoisonPill.getInstance(), self)
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
    awaitEntityResponse(messageApi, context, entityId) {
      // TODO Persist the given entity.
      LOG.error { "Persisting entities/components is not yet implemented." }
    }

    componentActorCache.allActors().forEach {
      it.tell(msg, sender)
      it.tell(PoisonPill.getInstance(), self)
    }
  }
}
