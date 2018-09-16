package net.bestia.zoneserver.actor.entity.component

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Terminated
import com.google.common.collect.HashBiMap
import mu.KotlinLogging
import net.bestia.zoneserver.entity.EntityService
import net.bestia.zoneserver.entity.component.ScriptComponent
import net.bestia.messages.ComponentChangedMessage
import net.bestia.zoneserver.actor.SpringExtension
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * Manages the [ScriptComponent] for an entity.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class ScriptComponentActor(
        private val entityId: Long,
        private val componentId: Long,
        private val entityService: EntityService
) : AbstractActor() {

  private val scriptActors = HashBiMap.create<String, ActorRef>()

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
            .match(ComponentChangedMessage::class.java, this::handleComponentUpdate)
            .match(Terminated::class.java, this::handleTerminated)
            .build()
  }

  /**
   * The script component has changed. We try to look into the changes and create or delete all
   * the actors managing the components since a script component might have several timed callbacks
   * with which we must now synchronize.
   */
  private fun handleComponentUpdate(msg: ComponentChangedMessage) {
    entityService.getComponent(componentId, ScriptComponent::class.java).ifPresent { c ->
      val currentActiveUids = scriptActors.keys
      val componentActiveUids = c.allScriptUids

      componentActiveUids
              .filter { cuid -> componentActiveUids.contains(cuid) }
              .map { c.getCallback(it) }
              .forEach { this.updateActor(it) }

      currentActiveUids
              .filter { cuid -> !componentActiveUids.contains(cuid) }
              .forEach { this.terminateActor(it) }

      componentActiveUids
              .filter { cuid -> !currentActiveUids.contains(cuid) }
              .map { c.getCallback(it) }
              .forEach { this.createActor(it) }
    }
  }

  private fun terminateActor(uid: String) {
    LOG.debug("Periodic script actor terminating: {}", uid)
    val ref = scriptActors[uid]
    ref?.tell(PoisonPill.getInstance(), self)
  }

  private fun createActor(callback: ScriptComponent.ScriptCallback) {
    val actor = SpringExtension.unnamedActorOf(context,
            PeriodicScriptActor::class.java, entityId,
            callback.intervalMs, callback.script)

    context.watch(actor)
  }

  private fun updateActor(callback: ScriptComponent.ScriptCallback) {
    val ref = scriptActors[callback.uuid]
    ref?.tell(callback, self)
  }

  /**
   * Handle the termination of the periodic movement and remove the actor ref
   * so we can start a new one.
   */
  private fun handleTerminated(term: Terminated) {
    LOG.debug("Periodic script actor terminated: {}", term.actor.toString())

    val termActor = term.actor()
    scriptActors.inverse().remove(termActor)
  }

  companion object {
    const val NAME = "scriptComponent"
  }
}
