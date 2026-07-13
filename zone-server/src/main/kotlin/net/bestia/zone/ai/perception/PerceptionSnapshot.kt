package net.bestia.zone.ai.perception

import net.bestia.zone.geometry.Vec3L

/**
 * The full result of one perception sweep for an NPC: everything it currently sees plus the moment
 * the snapshot was taken.
 */
data class PerceptionSnapshot(
  val selfPosition: Vec3L,
  val percepts: List<Percept>,
  val timestampMs: Long
) {
  val hostiles: List<Percept> get() = percepts.filter { it.hostile }
}