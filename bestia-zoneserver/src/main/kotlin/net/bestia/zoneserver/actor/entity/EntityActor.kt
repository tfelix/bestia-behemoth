package net.bestia.zoneserver.actor.entity

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Terminated
import com.google.common.collect.HashBiMap
import mu.KotlinLogging
import net.bestia.messages.entity.ComponentEnvelope
import net.bestia.messages.entity.ComponentIntall
import net.bestia.messages.entity.ComponentRemove
import net.bestia.messages.entity.EntityEnvelope
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

  /**
   * Saves the references for the actors managing components.
   */
  private val componentActors = HashBiMap.create<Long, ActorRef>()

  private var entityId: Long = 0

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
            .match(EntityEnvelope::class.java, this::handleEntityEnvelope)
            .match(Terminated::class.java, this::handleTerminated)
            .build()
  }

  private fun handleEntityEnvelope(envelope: EntityEnvelope) {
    checkStartup(envelope.entityId)
    val content = envelope.content
    when (content) {
      is ComponentEnvelope -> handleComponentEnvelope(content)
      else -> unhandled(content)
    }
  }

  /**
   * Checks if we have such a associated component and if so delivers the
   * message.
   */
  private fun handleComponentEnvelope(msg: ComponentEnvelope) {
    val compId = msg.componentId

    when (msg.content) {
      is ComponentIntall -> handleComponentInstall(compId)
      is ComponentRemove -> handleComponentRemove(compId)
      else -> trySendComponentActor(componentActors[compId], msg)
    }
  }

  private fun trySendComponentActor(compActor: ActorRef?, msg: ComponentEnvelope) {
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
