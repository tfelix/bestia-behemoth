package net.bestia.zoneserver.script.api

import net.bestia.zoneserver.entity.component.IntervalScriptCallback
import java.util.*

class ScriptApi(
    private val ctx: ScriptContext
) {

  fun livetime(livetimeMs: Long): ScriptApi {
    ctx.lifetimeMs = livetimeMs

    return this
  }

  fun setInterval(callback: String, delayMs: Long): ScriptApi {
    if (delayMs <= 0) {
      throw IllegalArgumentException("Delay must be bigger then 0.")
    }

    ctx.intervalCallback = IntervalScriptCallback(
        uuid = UUID.randomUUID().toString(),
        intervalMs = delayMs,
        script = callback
    )

    return this
  }
}