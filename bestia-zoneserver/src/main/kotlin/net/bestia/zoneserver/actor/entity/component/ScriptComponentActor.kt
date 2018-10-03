package net.bestia.zoneserver.actor.entity.component

import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Terminated
import akka.japi.pf.ReceiveBuilder
import com.google.common.collect.HashBiMap
import mu.KotlinLogging
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.entity.component.ScriptComponent
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
    scriptComponent: ScriptComponent
) : ComponentActor<ScriptComponent>(scriptComponent) {

  private val periodicScriptActor = HashBiMap.create<String, ActorRef>()

  override fun createReceive(builder: ReceiveBuilder) {
    builder.match(Terminated::class.java, this::handlePeriodicActorTerminated)
  }

  private fun handlePeriodicActorTerminated(msg: Terminated) {
    LOG.debug { "PeriodicScriptActor ${msg.actor.path()} terminated" }
    periodicScriptActor.inverse().remove(msg.actor)
  }

  override fun onComponentChanged(oldComponent: ScriptComponent, newComponent: ScriptComponent) {
    val periodicScriptActorsToRemove = periodicScriptActor.keys - component.scripts.keys
    periodicScriptActorsToRemove.forEach { killPeriodicActor(it) }

    val periodicScriptActorToAdd = component.scripts.keys - periodicScriptActor.keys
    periodicScriptActorToAdd.forEach {
      component.scripts[it]?.let { scriptCallback -> addPeriodicScriptActor(scriptCallback) }
    }
  }

  private fun killPeriodicActor(key: String) {
    periodicScriptActor[key]?.tell(PoisonPill.getInstance(), self)
    periodicScriptActor.remove(key)
  }

  private fun addPeriodicScriptActor(scriptCallback: ScriptComponent.ScriptCallback) {
    LOG.debug { "PeriodicScriptActor $scriptCallback added" }
    val periodicActor = SpringExtension.actorOf(context, PeriodicScriptActor::class.java)
    periodicScriptActor[scriptCallback.uuid] = periodicActor
  }

  companion object {
    const val NAME = "scriptComponent"
  }
}
