package net.bestia.zoneserver.actor.entity.component

import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Terminated
import akka.japi.pf.ReceiveBuilder
import com.google.common.collect.HashBiMap
import mu.KotlinLogging
import net.bestia.messages.entity.EntityMessage
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.entity.component.IntervalScriptCallback
import net.bestia.zoneserver.entity.component.ScriptCallback
import net.bestia.zoneserver.entity.component.ScriptComponent
import java.lang.IllegalStateException
import java.time.Duration

private val LOG = KotlinLogging.logger { }

data class SetTimeoutCommand(
    override val entityId: Long,
    val timeout: Duration,
    val callbackFn: String
) : EntityMessage, ComponentMessage<ScriptComponent> {
  override val componentType: Class<out ScriptComponent>
    get() = ScriptComponent::class.java
}

data class SetIntervalCommand(
    override val entityId: Long,
    val uuid: String,
    val timeout: Duration,
    val callbackFn: String
) : EntityMessage, ComponentMessage<ScriptComponent> {
  override val componentType: Class<out ScriptComponent>
    get() = ScriptComponent::class.java
}

/**
 * Manages the [ScriptComponent] for an entity.
 *
 * @author Thomas Felix
 */
@ActorComponent(ScriptComponent::class)
class ScriptComponentActor(
    scriptComponent: ScriptComponent
) : ComponentActor<ScriptComponent>(scriptComponent) {

  private val periodicScriptActor = HashBiMap.create<String, ActorRef>()

  override fun createReceive(builder: ReceiveBuilder) {
    builder
        .match(Terminated::class.java, this::handlePeriodicActorTerminated)
        .match(SetIntervalCommand::class.java, this::addInterval)
        .match(SetTimeoutCommand::class.java, this::addTimeout)
  }

  private fun addTimeout(msg: SetTimeoutCommand) {
    throw IllegalStateException("No implemented")
  }

  private fun addInterval(msg: SetIntervalCommand) {
    val intervalCallback = IntervalScriptCallback(
        uuid = msg.uuid,
        interval = msg.timeout,
        scriptKeyCallback = msg.callbackFn,
        scriptEntityId = component.entityId
    )
    addPeriodicScriptActor(intervalCallback)
  }

  private fun handlePeriodicActorTerminated(msg: Terminated) {
    LOG.debug { "PeriodicScriptActor ${msg.actor.path()} terminated" }
    periodicScriptActor.inverse().remove(msg.actor)
  }

  override fun onComponentChanged(oldComponent: ScriptComponent, newComponent: ScriptComponent) {
    val periodicScriptActorsToRemove = periodicScriptActor.keys - component.scripts.keys
    periodicScriptActorsToRemove.forEach { killPeriodicActor(it) }
  }

  private fun killPeriodicActor(key: String) {
    periodicScriptActor[key]?.tell(PoisonPill.getInstance(), self)
  }

  private fun addPeriodicScriptActor(scriptCallback: ScriptCallback) {
    LOG.debug { "PeriodicScriptActor $scriptCallback added" }
    val periodicActor = SpringExtension.actorOf(context, IntervalScriptActor::class.java, scriptCallback)
    periodicScriptActor[scriptCallback.uuid] = periodicActor
  }

  companion object {
    const val NAME = "scriptComponent"
  }
}
