package net.bestia.zone.worldevent.rift

import net.bestia.zone.worldevent.persistence.StoredRift
import net.bestia.zone.worldevent.persistence.StoredRift.Phase
import kotlin.math.hypot

/**
 * How rifts move on, one step at a time: a knot pulls quietly until it opens or loses its hold, an open rift pulls
 * harder through five stages until it collapses, and touching rifts merge. Pure over the field, so a whole rift
 * can run in a test. The caller holds the field's lock.
 */
class RiftLifecycle(
  private val config: RiftConfig,
  private val field: RiftField,
  private val realSecondsPerBestiaSecond: Double,
) {

  /** What changed in one step that someone outside should hear about. */
  enum class News { OPENED, COLLAPSED, DISSOLVED }

  /** Moves [rift] on by [seconds] real seconds to Bestia second [now]. */
  fun step(rift: StoredRift, now: Long, seconds: Double): News? = when (rift.phase) {
    Phase.KNOT -> stepKnot(rift, now, seconds)
    Phase.OPEN -> stepOpen(rift, now, seconds)
    else -> null
  }

  /** How far [rift] pulls right now, in metres. */
  fun radiusOf(rift: StoredRift): Double = when (rift.phase) {
    Phase.OPEN -> config.knotRadiusMetres +
      (config.openRadiusMetres - config.knotRadiusMetres) * (rift.stage - 1).coerceAtLeast(0) / (STAGES - 1)
    else -> config.knotRadiusMetres
  }

  /** The Bestia seconds an open rift stands before it collapses. */
  fun openBestiaSeconds(): Long = (config.openRealHours * 3600 / realSecondsPerBestiaSecond).toLong()

  /** The older of two touching rifts swallows the younger, its mana and its progress. */
  fun merge(rifts: List<StoredRift>): List<StoredRift> {
    val merged = ArrayList<StoredRift>()
    val alive = rifts.filter { it.phase.isActive }.sortedBy { it.id }.toMutableList()

    for (older in alive.toList()) {
      if (!older.phase.isActive) continue
      alive.filter { it.id > older.id && it.phase.isActive && touch(older, it) }.forEach { younger ->
        older.reservoir += younger.reservoir
        younger.reservoir = 0.0
        if (younger.phase == Phase.OPEN && older.phase == Phase.KNOT) {
          older.phase = Phase.OPEN
          older.collapsesAtSecond = younger.collapsesAtSecond
        }
        older.stage = maxOf(older.stage, younger.stage)
        younger.phase = Phase.MERGED
        merged += younger
      }
    }
    return merged
  }

  private fun stepKnot(rift: StoredRift, now: Long, seconds: Double): News? {
    if (field.ringDensity(rift.x, rift.y, config.knotRadiusMetres) < config.dissolveBelow) {
      // Lost its hold: what it gathered goes back quietly, over the ground it came from and the ring around it.
      field.spill(rift.x, rift.y, 2 * config.knotRadiusMetres, rift.reservoir)
      rift.reservoir = 0.0
      rift.phase = Phase.DISSOLVED
      return News.DISSOLVED
    }

    if (now >= rift.opensAtSecond) {
      rift.phase = Phase.OPEN
      rift.stage = 1
      rift.opensAtSecond = now
      rift.collapsesAtSecond = now + openBestiaSeconds()
      return News.OPENED
    }

    rift.reservoir += pull(rift, config.knotPullPerMinute, seconds)
    return null
  }

  private fun stepOpen(rift: StoredRift, now: Long, seconds: Double): News? {
    if (now >= rift.collapsesAtSecond) {
      field.spill(rift.x, rift.y, config.spillRadiusMetres, rift.reservoir)
      rift.reservoir = 0.0
      rift.phase = Phase.COLLAPSED
      return News.COLLAPSED
    }

    val stageSeconds = openBestiaSeconds() / STAGES
    rift.stage = (1 + (now - rift.opensAtSecond) / stageSeconds).toInt().coerceIn(1, STAGES)
    rift.reservoir += pull(rift, config.openPullPerMinute, seconds)
    return null
  }

  private fun pull(rift: StoredRift, perMinute: Double, seconds: Double): Double =
    field.pull(rift.x, rift.y, radiusOf(rift), perMinute * seconds / 60.0, config.drainedDensity)

  private fun touch(a: StoredRift, b: StoredRift): Boolean = hypot(a.x - b.x, a.y - b.y) <= radiusOf(a) + radiusOf(b)

  companion object {
    const val STAGES = 5
  }
}
