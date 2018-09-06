package net.bestia.zoneserver.script.api

import net.bestia.entity.EntityService
import net.bestia.entity.component.ScriptComponent
import net.bestia.entity.component.ScriptComponent.ScriptCallback
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

    val scriptComp = entityService.getComponentOrCreate(entityId, ScriptComponent::class.java) ?: return this
    val scriptUuid = UUID.randomUUID().toString()
    val scriptCallback = ScriptCallback(
            scriptUuid,
            ScriptComponent.TriggerType.ON_INTERVAL,
            callback,
            delayMs
    )
    scriptComp.addCallback(scriptCallback)
    entityService.updateComponent(scriptComp)
    return this
  }
}