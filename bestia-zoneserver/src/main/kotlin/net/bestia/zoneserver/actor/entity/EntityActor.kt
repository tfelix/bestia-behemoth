package net.bestia.zoneserver.actor.entity

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Terminated
import com.google.common.collect.HashBiMap
import mu.KotlinLogging
import net.bestia.messages.entity.*
import net.bestia.zoneserver.actor.AwaitResponseActor
import net.bestia.zoneserver.actor.entity.component.EntityComponentActorFactory
import net.bestia.zoneserver.entity.Entity
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

internal data class RequestEntity(
    val requester: ActorRef,
    val context: Any? = null
)

internal data class ResponseEntity(
    val entity: Entity,
    val content: Any? = null
)

internal object SaveAndKill

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

  private class ComponentActorCache {

    private val classToActor = HashBiMap.create<Class<*>, ActorRef>()

    val size: Int get() = classToActor.size

    fun <T : net.bestia.zoneserver.entity.component.Component> add(component: T, compActor: ActorRef) {
      classToActor[component.javaClass] = compActor
    }

    fun allActors(): List<ActorRef> {
      return classToActor.values.toList()
    }

    fun remove(actor: ActorRef) {
      classToActor.inverse().remove(actor)
    }

    fun <T : net.bestia.zoneserver.entity.component.Component> get(componentClass: Class<T>): ActorRef? {
      return classToActor[componentClass]
    }

    fun getAllCachedComponentClasses(): Set<Class<out net.bestia.zoneserver.entity.component.Component>> {
      return classToActor.keys.asSequence().filter {
        Component::class.java.isAssignableFrom(it)
      }.map {
        @Suppress("UNCHECKED_CAST")
        it as Class<out net.bestia.zoneserver.entity.component.Component>
      }.toSet()
    }
  }

  private val componentActorCache = ComponentActorCache()
  private var entityId: Long = 0

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
        .match(AddComponentMessage::class.java, this::handleComponentInstall)
        .match(ComponentClassEnvelope::class.java, this::handleComponentClassEnvelope)
        .match(ComponentBroadcastEnvelope::class.java, this::handleAllComponentBroadcast)
        .match(ComponentRequestMessage::class.java, this::handleComponentRequest)
        .match(DeleteComponentMessage::class.java, this::handleComponentRemove)

        .match(Terminated::class.java, this::handleTerminated)

        .match(SaveAndKill::class.java, this::handleSaveAndKill)
        .match(RequestEntity::class.java, this::handleEntityRequest)
        .matchAny(this::terminateIfNoSuitableMessage)
        .build()
  }

  private fun handleEntityRequest(msg: RequestEntity) {
    val awaitedComponentClasses = componentActorCache.getAllCachedComponentClasses()
    val hasAllResponses = { receivedResponses: List<Any> ->
      awaitedComponentClasses.containsAll(receivedResponses)
    }
    val waitResponseProps = AwaitResponseActor.props(hasAllResponses) {
      val entity = Entity.withComponents(
          entityId,
          it.getAllResponses(net.bestia.zoneserver.entity.component.Component::class)
      )
      val entityResponse = ResponseEntity(
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
      LOG.info { "EntityActor $entityId received message: $msg. Awaited AddComponentMessage. Terminate." }
      context.stop(self)
    }
  }

  private fun handleComponentInstall(msg: AddComponentMessage<*>) {
    LOG.debug { "Installing component: ${msg.component} on entity: $entityId." }

    val compActor = factory.startActor(context, msg.component) ?: run {
      LOG.warn { "Component actor for comp id ${msg.component} was not created." }
      return
    }

    context().watch(compActor)
    componentActorCache.add(msg.component, compActor)
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
    if (componentActorCache.size == 0) {
      context.stop(self)
    }
  }

  private fun handleSaveAndKill(msg: SaveAndKill) {
    componentActorCache.allActors().forEach {
      it.tell(msg, sender)
      it.tell(PoisonPill.getInstance(), self)
    }
  }
}
