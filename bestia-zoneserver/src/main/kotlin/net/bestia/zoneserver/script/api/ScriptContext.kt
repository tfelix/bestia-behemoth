package net.bestia.zoneserver.script.api

import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.actor.entity.component.ScriptLifetime
import net.bestia.zoneserver.actor.entity.makeEntityComponentEnvelope
import net.bestia.zoneserver.entity.component.DelayScriptCallback
import net.bestia.zoneserver.entity.component.IntervalScriptCallback
import net.bestia.zoneserver.entity.component.ScriptComponent

class ScriptContext(
    private val entityId: Long
) : Context {
  var lifetimeMs: Long? = null
  var intervalCallback: IntervalScriptCallback? = null
  var delayCallback: DelayScriptCallback? = null

  override fun commitEntityUpdates(messageApi: MessageApi) {
    lifetimeMs?.let {
      val parcel = makeEntityComponentEnvelope(
          entityId,
          ScriptComponent::class.java,
          ScriptLifetime(it)
      )
      messageApi.send(parcel)
    }

    intervalCallback?.let {
      val parcel = makeEntityComponentEnvelope(
          entityId,
          ScriptComponent::class.java,
          it
      )
      messageApi.send(parcel)
    }

    delayCallback?.let {
      val parcel = makeEntityComponentEnvelope(
          entityId,
          ScriptComponent::class.java,
          it
      )
      messageApi.send(parcel)
    }
  }
}