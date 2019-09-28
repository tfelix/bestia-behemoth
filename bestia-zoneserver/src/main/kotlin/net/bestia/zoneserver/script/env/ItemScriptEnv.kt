package net.bestia.zoneserver.script.env

import net.bestia.model.geometry.Vec3

class ItemScriptEnv private constructor(
    private val userId: Long,
    private val targetId: Long?,
    private val targetPosition: Vec3? = null
) : ScriptEnv {

  constructor(userId: Long, targetId: Long) : this(userId, targetId, null)
  constructor(userId: Long, targetPosition: Vec3) : this(userId, null, targetPosition)

  override fun setupEnvironment(bindings: MutableMap<String, Any?>) {
    bindings["SELF"] = userId
    bindings["TARGET_ENTITY"] = targetId
    bindings["TARGET_POSITION"] = targetPosition
  }
}
