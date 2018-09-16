package net.bestia.zoneserver.actor.entity

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Terminated
import com.google.common.collect.HashBiMap
import mu.KotlinLogging
import net.bestia.messages.entity.*
import net.bestia.zoneserver.ComponentEnvelope2
import net.bestia.zoneserver.actor.entity.component.AddComponentMessage
import net.bestia.zoneserver.actor.entity.component.ComponentBroadcastEnvelope
import net.bestia.zoneserver.actor.entity.component.EntityComponentActorFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

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
        private val factory: EntityComponentActorFactory
) : AbstractActor() {

  private data class ComponentKey(
          val componentId: Long,
          val componentClass: Class<net.bestia.zoneserver.entity.component.Component>
  )

  private val componentActors = HashBiMap.create<ComponentKey, ActorRef>()
  private var entityId: Long = 0

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
            .match(EntityEnvelope::class.java, this::handleEntityEnvelope)
            .match(Terminated::class.java, this::handleTerminated)
            .matchAny(this::terminateIfNoSuitableMessage)
            .build()
  }

  private fun terminateIfNoSuitableMessage(msg: Any) {
    if (entityId == 0L && msg !is AddComponentMessage<*>) {
      LOG.info { "EntityActor $entityId received message: $msg. Awaited AddComponentMessage. Terminate." }
      context.stop(self)
    }
  }

  private fun handleEntityEnvelope(envelope: EntityEnvelope) {
    checkStartup(envelope.entityId)
    val content = envelope.content
    when (content) {
      is AddComponentMessage<*> -> handleComponentInstall(content)
      is ComponentIdEnvelope -> handleComponentEnvelope(content)
      is ComponentClassEnvelope<*> -> handleComponentEnvelope2(content)
      is ComponentBroadcastEnvelope -> handleAllComponentBroadcast(content)
      else -> unhandled(content)
    }
  }

  private fun handleComponentInstall(msg: AddComponentMessage<*>) {
    LOG.debug { "Installing component: ${msg.component.id} on entity: $entityId." }

    val compActor = factory.startActor(context, msg.component) ?: run {
      LOG.warn { "Component actor for comp id ${msg.component.id} was not created." }
      return
    }

    context().watch(compActor)
    // Maybe this is dump and it would be better two have two collections are a dedicated
    // cache class for this kind of lookups.
    val key = ComponentKey(msg.component.id, msg.component.javaClass)
    componentActors[key] = compActor
  }

  private fun handleAllComponentBroadcast(msg: ComponentBroadcastEnvelope) {
    LOG.debug { "Entity received $msg" }
    componentActors.values.forEach { it.tell(msg.content, sender) }
  }

  /**
   * Checks if we have such a associated component and if so delivers the
   * message.
   */
  private fun handleComponentEnvelope(msg: ComponentIdEnvelope) {
    val compId = msg.componentId

    when (msg.content) {
      is ComponentIntall -> handleComponentInstall(compId)
      is ComponentRemove -> handleComponentRemove(compId)
      else -> trySendComponentActor(componentActors[compId], msg)
    }
  }

  private fun handleComponentEnvelope2(msg: ComponentEnvelope2<*>) {
    componentActors2[msg.componentClass]?.forward(msg.content, context)
  }

  private fun trySendComponentActor(compActor: ActorRef?, msg: ComponentIdEnvelope) {
    when (compActor) {
      null -> {
        LOG.debug("Component message unhandled: {}.", msg)
        unhandled(msg)
      }
      else -> {
        LOG.debug("Forwarding comp message: {} to: {}.", msg, compActor)
        compActor.tell(msg.content, self)
      }
    }
  }

  private fun handleComponentInstall(componentId: Long) {
    LOG.debug("Installing component: {} on entity: {}.", componentId, entityId)

    val compActor = factory.startActor(context, componentId)

    if (compActor == null) {
      LOG.warn("Component actor for comp id {} was not created.", componentId)
      return
    }

    context().watch(compActor)
    componentActors[componentId] = compActor
  }

  private fun handleComponentRemove(componentId: Long) {
    LOG.debug { "Removing component: $componentId on entity: $entityId" }
    componentActors[componentId]?.tell(PoisonPill.getInstance(), self)
  }

  /**
   * Terminate itself when there is no active component anymore.
   */
  private fun handleTerminated(term: Terminated) {
    componentActors.inverse().remove(term.actor())
    if (componentActors.size == 0) {
      context.stop(self)
    }
  }

  /**
   * Checks if we have already a defined entity id and if not we use it.
   */
  private fun checkStartup(entityId: Long) {
    if (entityId == 0L) {
      this.entityId = entityId
    }
  }
}
