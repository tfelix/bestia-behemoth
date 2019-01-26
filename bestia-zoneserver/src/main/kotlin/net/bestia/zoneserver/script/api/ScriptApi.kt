package net.bestia.zoneserver.script.api

import net.bestia.zoneserver.entity.component.DelayScriptCallback
import net.bestia.zoneserver.entity.component.IntervalScriptCallback
import java.util.*

class ScriptApi(
    private val ctx: ScriptContext
) {

  fun setLivetime(livetimeMs: Long): ScriptApi {
    ctx.lifetimeMs = livetimeMs

    return this
  }

  fun delay(delayMs: Long, callback: String): ScriptApi {
    ctx.delayCallback = DelayScriptCallback(
        uuid = UUID.randomUUID().toString(),
        script = callback,
        delayMs = delayMs
    )

    return this
  }

  fun setInterval(delayMs: Long, callback: String): ScriptApi {
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