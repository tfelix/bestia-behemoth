package net.bestia.zoneserver.script.api

import net.bestia.messages.entity.EntityMessage
import net.bestia.zoneserver.actor.entity.component.SetIntervalCommand
import net.bestia.zoneserver.actor.entity.component.SetTimeoutCommand
import java.time.Duration

class ScriptApi(
    private val entityId: Long,
    private val commands: MutableList<EntityMessage>
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