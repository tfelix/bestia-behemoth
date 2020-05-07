package net.bestia.zoneserver.entity.component

import java.time.Duration

sealed class ScriptCallback {
  abstract val uuid: String
  abstract val scriptKeyCallback: String
  abstract val scriptEntityId: Long
}

data class TimeoutScriptCallback(
    override val uuid: String,
    override val scriptEntityId: Long,
    override val scriptKeyCallback: String,
    val delayMs: Long
) : ScriptCallback()

data class IntervalScriptCallback(
    override val uuid: String,
    override val scriptEntityId: Long,
    override val scriptKeyCallback: String,
    val interval: Duration
) : ScriptCallback()

data class OnEnterScriptCallback(
    override val uuid: String,
    override val scriptEntityId: Long,
    override val scriptKeyCallback: String
) : ScriptCallback()

data class OnLeaveScriptCallback(
    override val uuid: String,
    override val scriptEntityId: Long,
    override val scriptKeyCallback: String
) : ScriptCallback()

/**
 * Holds various script callbacks for entities.
 *
 * @author Thomas Felix
 */
data class ScriptComponent(
    override val entityId: Long,
    val scripts: Map<String, ScriptCallback> = emptyMap(),
    val scriptVars: Map<String, Map<String, Any>> = emptyMap()
) : Component