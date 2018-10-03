package net.bestia.zoneserver.script.api

import net.bestia.zoneserver.entity.EntityService
import net.bestia.zoneserver.entity.component.ScriptComponent
import net.bestia.zoneserver.entity.component.ScriptComponent.ScriptCallback
import java.util.*

class ScriptApi(
        private val rootApi: ScriptRootApi,
        private val entityId: Long,
        private val entityService: EntityService
) : ScriptChildApi {

  override fun and(): ScriptRootApi {
    return rootApi
  }

  fun setCallbackOnce(callback: String, delayMs: Int): ScriptApi {
    return this
  }

  fun setLivetime(delayMs: Int): ScriptApi {
    return this
  }

  fun setInterval(callback: String, delayMs: Int): ScriptApi {
    if (delayMs <= 0) {
      throw IllegalArgumentException("Delay must be bigger then 0.")
    }

    val scriptComp = ScriptComponent(entityId)
    val scriptUuid = UUID.randomUUID().toString()
    val scriptCallback = ScriptCallback(
            scriptUuid,
            ScriptComponent.TriggerType.ON_INTERVAL,
            callback,
            delayMs
    )
    scriptComp.addScriptCallback(scriptCallback)
    entityService.updateComponent(scriptComp)
    return this
  }
}