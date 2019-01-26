package net.bestia.zoneserver.entity.component

import java.util.*

sealed class ScriptCallback {
  abstract val uuid: String
  abstract val script: String
}

data class DelayScriptCallback(
    override val uuid: String,
    override val script: String,
    val delayMs: Long
) : ScriptCallback()

data class IntervalScriptCallback(
    override val uuid: String,
    override val script: String,
    val intervalMs: Long
) : ScriptCallback()

data class OnEnterScriptCallback(
    override val uuid: String,
    override val script: String
) : ScriptCallback()

data class OnLeaveScriptCallback(
    override val uuid: String,
    override val script: String
) : ScriptCallback()

/**
 * Holds various script callbacks for entities.
 *
 * @author Thomas Felix
 */
data class ScriptComponent(
    override val entityId: Long,
    val scripts: Map<String, ScriptCallback>
) : Component {

  companion object {
    fun interval(scriptName: String, intervalMs: Long): IntervalScriptCallback {
      return IntervalScriptCallback(
          UUID.randomUUID().toString(),
          scriptName,
          intervalMs
      )
    }
  }
}