package net.bestia.zoneserver.script.exec

import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.script.ScriptKeyBuilder
import net.bestia.zoneserver.script.ScriptType

data class ItemScriptExec private constructor(
    override val scriptKey: String,
    val userId: Long,
    val targetId: Long?,
    val targetPosition: Vec3?
) : ScriptExec {

  override fun setupEnvironment(bindings: MutableMap<String, Any?>) {
    bindings["USER_ENTITY_ID"] = userId
    bindings["TARGET_ENTITY"] = targetId
    bindings["TARGET_POSITION"] = targetPosition
  }

  class Builder {
    var itemDbName: String? = null
    var user: Entity? = null
    var targetEntity: Entity? = null
    var targetPoint: Vec3? = null

    fun build(): ItemScriptExec {
      require(itemDbName != null) { "Item name must be given" }
      require(user != null) { "User entity must not be null" }

      return ItemScriptExec(
          scriptKey = ScriptKeyBuilder.getScriptKey(ScriptType.ITEM, itemDbName!!),
          userId = user!!.id,
          targetId = targetEntity?.id,
          targetPosition = targetPoint
      )
    }
  }
}