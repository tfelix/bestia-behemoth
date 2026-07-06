package net.bestia.zone.ai.memory

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/**
 * A single remembered fact, e.g. "I saw enemy 42 at (3,5) a moment ago". Entries decay over time:
 * [confidence] models how strongly the fact is believed and [expiresAtMs] the wall-clock time after
 * which the memory should be dropped.
 */
data class MemoryEntry(
  val key: String,
  val type: MemoryType,
  val position: Vec3L?,
  val entityId: EntityId?,
  val timestampMs: Long,
  val confidence: Double,
  val expiresAtMs: Long,
  val scope: MemoryScope
) {

  fun isExpired(nowMs: Long): Boolean = nowMs >= expiresAtMs

  enum class MemoryType {
    ENEMY_SIGHTING,
    DAMAGE_SOURCE,
    POINT_OF_INTEREST
  }
}
