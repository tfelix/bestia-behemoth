package net.bestia.zoneserver.script.api

import mu.KotlinLogging
import net.bestia.messages.entity.EntityMessage
import net.bestia.zoneserver.actor.entity.component.SetIntervalCommand
import net.bestia.zoneserver.actor.entity.component.SetTimeoutCommand
import java.time.Duration
import java.util.*

private val LOG = KotlinLogging.logger { }

class ScriptApi(
    private val entityId: Long,
    private val scriptKey: String,
    private val uuid: String = UUID.randomUUID().toString(),
    private val commands: MutableList<EntityMessage>
) {

  fun timeout(delayMs: Long, callback: String): ScriptApi {
    LOG.trace { "${scriptKey}: timeout($delayMs: Long, $callback: String)" }
    require(delayMs > 0) { "delayMs must be bigger then 0" }

    commands.add(SetTimeoutCommand(
        entityId = entityId,
        timeout = Duration.ofMillis(delayMs),
        callbackFn = "$scriptKey::$callback"
    ))

    return this
  }

  fun setInterval(delayMs: Long, callback: String): ScriptApi {
    LOG.trace { "${scriptKey}: setInterval($delayMs: Long, $callback: String)" }
    require(delayMs > 0) { "delayMs must be bigger then 0" }

    commands.add(SetIntervalCommand(
        entityId = entityId,
        timeout = Duration.ofMillis(delayMs),
        callbackFn = "$scriptKey::$callback",
        uuid = uuid
    ))

    return this
  }
}