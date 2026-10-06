package net.bestia.zone.engine

/**
 * Decides how many fixed steps the tick loop owes. Every step is exactly [stepNanos] long, so systems
 * always see the same delta. A backlog longer than [maxCatchUpSteps] is dropped instead of replayed:
 * after a long pause the world runs slow for a moment rather than racing to catch up.
 */
class FixedStepClock(
  val stepNanos: Long,
  private val maxCatchUpSteps: Int,
  startNanos: Long,
) {
  init {
    require(stepNanos > 0) { "A step must have a length" }
    require(maxCatchUpSteps >= 1) { "At least the step that is due has to run" }
  }

  /** When the next step is due, on the [System.nanoTime] scale. */
  var nextStepAt: Long = startNanos
    private set

  /** Steps skipped since this clock started, because they were more than [maxCatchUpSteps] behind. */
  var droppedSteps: Long = 0L
    private set

  /** Steps to run now; also moves [nextStepAt] past every step it accounts for, run or dropped. */
  fun dueSteps(nowNanos: Long): Int {
    if (nowNanos < nextStepAt) {
      return 0
    }

    val owed = (nowNanos - nextStepAt) / stepNanos + 1
    nextStepAt += owed * stepNanos

    if (owed > maxCatchUpSteps) {
      droppedSteps += owed - maxCatchUpSteps
      return maxCatchUpSteps
    }

    return owed.toInt()
  }
}
