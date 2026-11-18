package net.bestia.zone.workexp

import net.bestia.zone.ecs.core.Component
import kotlin.math.max
import kotlin.math.min

/**
 * The point in time up to which a master's effort has been paid, as epoch millis. Effort is paid at most one second
 * per second, and only the last `bankSeconds` of unpaid time can be banked.
 *
 * Kept on the master row, not rebuilt on login: a bucket that starts full on every login refills by relogging.
 */
class WorkExpLedger(var paidUntilMs: Long = 0L) : Component {

  /** Takes up to [effortSeconds] of the unpaid time before [nowMs] and returns how much was taken. */
  fun draw(nowMs: Long, effortSeconds: Double, bankSeconds: Int): Double {
    val start = max(paidUntilMs, nowMs - bankSeconds * MILLIS_PER_SECOND)
    val takenMs = min((effortSeconds * MILLIS_PER_SECOND).toLong(), nowMs - start).coerceAtLeast(0L)

    paidUntilMs = start + takenMs
    return takenMs / MILLIS_PER_SECOND.toDouble()
  }

  private companion object {
    const val MILLIS_PER_SECOND = 1000L
  }
}
