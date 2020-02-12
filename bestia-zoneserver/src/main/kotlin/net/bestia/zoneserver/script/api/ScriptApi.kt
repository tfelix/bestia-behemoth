package net.bestia.zoneserver.script.api

import net.bestia.zoneserver.actor.entity.EntityEnvelope
import net.bestia.zoneserver.actor.entity.component.ComponentEnvelope
import net.bestia.zoneserver.entity.component.ScriptComponent
import java.time.Duration

sealed class ScriptCommand : EntityCommand {
  abstract val entityId: Long

  override fun toEntityEnvelope(): EntityEnvelope {
    return EntityEnvelope(
        entityId = entityId,
        content = ComponentEnvelope(
            componentType = ScriptComponent::class.java,
            content = this
        )
    )
  }
}

data class SetTimeoutCommand(
    override val entityId: Long,
    val timeout: Duration,
    val callbackFn: String
) : ScriptCommand()

data class SetIntervalCommand(
    override val entityId: Long,
    val timeout: Duration,
    val callbackFn: String
) : ScriptCommand()

class ScriptApi(
    private val entityId: Long,
    private val commands: MutableList<EntityCommand>
) {

  fun timeout(delayMs: Long, callback: String): ScriptApi {
    require(delayMs > 0) { "delayMs must be bigger then 0" }
    commands.add(SetTimeoutCommand(
        entityId = entityId,
        timeout = Duration.ofMillis(delayMs),
        callbackFn = callback
    ))

    return this
  }

  fun setInterval(delayMs: Long, callback: String): ScriptApi {
    require(delayMs > 0) { "Delay must be bigger then 0" }
    commands.add(SetIntervalCommand(
        entityId = entityId,
        timeout = Duration.ofMillis(delayMs),
        callbackFn = callback
    ))

    return this
  }
}