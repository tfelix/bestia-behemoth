package net.bestia.zoneserver.script.env

import net.bestia.model.geometry.Point

class ItemScriptEnv private constructor(
        private val userId: Long,
        private val targetId: Long,
        private val targetPosition: Point? = null) : ScriptEnv {

  constructor(userId: Long, targetId: Long) : this(userId, targetId,  null)
  constructor(userId: Long, targetPosition: Point) : this(userId, 0, targetPosition)

  override fun setupEnvironment(bindings: MutableMap<String, Any?>) {
    bindings["SELF"] = userId
    bindings["TARGET_ENTITY"] = targetId
    bindings["TARGET_POSITION"] = targetPosition
  }
}
