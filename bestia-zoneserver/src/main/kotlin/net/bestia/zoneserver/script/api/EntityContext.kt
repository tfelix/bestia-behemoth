package net.bestia.zoneserver.script.api

import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.actor.entity.EntityEnvelope
import net.bestia.zoneserver.actor.entity.component.ComponentEnvelope
import net.bestia.zoneserver.chat.PositionToMessage
import net.bestia.zoneserver.entity.component.PositionComponent

data class EntityContext(
    val entityId: Long
) : Context {
  var position: PositionToMessage? = null

  var condition: EntityConditionContext? = null
  var script: ScriptContext? = null

  override fun commitEntityUpdates(messageApi: MessageApi) {
    position?.let {
      messageApi.send(
          EntityEnvelope(
              entityId,
              ComponentEnvelope(PositionComponent::class.java, it)
          )
      )
    }

    condition?.let { it.commitEntityUpdates(messageApi) }
    script?.let { it.commitEntityUpdates(messageApi) }
  }
}