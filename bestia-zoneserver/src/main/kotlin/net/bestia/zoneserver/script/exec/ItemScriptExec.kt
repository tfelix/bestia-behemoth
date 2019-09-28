package net.bestia.zoneserver.script.exec

import net.bestia.model.geometry.Vec3
import net.bestia.model.item.PlayerItem
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.script.ScriptKeyBuilder
import net.bestia.zoneserver.script.ScriptType

data class ItemScriptExec private constructor(
    override val scriptKey: String,
    override val callbackFunction: String?,
    val userId: Long,
    val targetId: Long?,
    val targetPosition: Vec3?
) : ScriptExec {

  override fun setupEnvironment(bindings: MutableMap<String, Any?>) {
    bindings["SELF"] = userId
    bindings["TARGET_ENTITY"] = targetId
    bindings["TARGET_POSITION"] = targetPosition
  }

  class Builder() {
    var item: PlayerItem? = null
    var user: Entity? = null
    var targetEntity: Entity? = null
    var targetPoint: Vec3? = null

    fun build(): ItemScriptExec {
      require(user != null) { "User entity must not be null" }
      require(targetEntity != null || targetPoint != null) { "Either targetEntity or targetPoint must not be null" }

      return ItemScriptExec(
          scriptKey = ScriptKeyBuilder.getScriptKey(ScriptType.ITEM, item!!.item.itemDbName),
          callbackFunction = null,
          userId = user!!.id,
          targetId = targetEntity?.id,
          targetPosition = targetPoint
      )
    }
  }
}