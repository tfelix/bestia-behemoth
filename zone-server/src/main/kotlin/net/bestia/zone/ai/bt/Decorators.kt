package net.bestia.zone.ai.bt

import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.BtNode
import net.bestia.zone.ai.core.behavior.Status

/**
 * Swaps SUCCESS and FAILURE; RUNNING passes through untouched.
 *
 * Mostly used to turn a condition into its negation — `invert { condition("in range") { ... } }` —
 * without needing a second leaf for every "not" case.
 */
class Inverter(private val child: BtNode) : BtNode {
  override fun tick(context: BtContext): Status = when (child.tick(context)) {
    Status.SUCCESS -> Status.FAILURE
    Status.FAILURE -> Status.SUCCESS
    Status.RUNNING -> Status.RUNNING
  }
}

/**
 * Turns FAILURE into SUCCESS, so an optional step cannot abort the sequence around it.
 *
 * "Try to play the roar animation, but do not abandon the attack if it is unavailable."
 */
class Succeeder(private val child: BtNode) : BtNode {
  override fun tick(context: BtContext): Status = when (val status = child.tick(context)) {
    Status.FAILURE -> Status.SUCCESS
    else -> status
  }
}

/**
 * Re-runs [child] until it has succeeded [times] times, then succeeds. A child FAILURE fails the
 * whole decorator immediately — a repeat that swallowed failures would spin forever on something
 * that can never succeed.
 *
 * Holds a counter, which is safe because a tree instance belongs to exactly one adoption of one
 * action (see `BtNode`).
 */
class Repeat(private val child: BtNode, private val times: Int) : BtNode {

  init {
    require(times >= 1) { "Repeat requires times >= 1, got $times" }
  }

  private var completed = 0

  override fun tick(context: BtContext): Status {
    if (completed >= times) return Status.SUCCESS

    return when (child.tick(context)) {
      Status.SUCCESS -> {
        completed++
        if (completed >= times) Status.SUCCESS else Status.RUNNING
      }

      Status.FAILURE -> Status.FAILURE
      Status.RUNNING -> Status.RUNNING
    }
  }
}

/**
 * Reports FAILURE while cooling down, and only lets [child] run once at least [seconds] of simulated
 * time have passed since it last succeeded.
 *
 * This is the reusable form of the hand-rolled attack cooldown that used to live as a mutable field
 * on the brain component and be counted down by the act system itself. Wrapping the strike instead
 * keeps the timing next to the thing being timed, and lets any action be rate-limited the same way.
 *
 * Cooling down is FAILURE rather than RUNNING on purpose: inside a [SelectorNode] that lets a
 * lower-priority alternative run during the gap, which is what makes "strike if you can, otherwise
 * reposition" expressible. Wrap it in [Succeeder] if you want the gap to be a no-op instead.
 *
 * The first tick is always allowed through — a fresh tree is not on cooldown.
 */
class Cooldown(private val child: BtNode, private val seconds: Float) : BtNode {

  init {
    require(seconds > 0f) { "Cooldown requires seconds > 0, got $seconds" }
  }

  private var remaining = 0f

  override fun tick(context: BtContext): Status {
    if (remaining > 0f) {
      remaining = (remaining - context.deltaTime).coerceAtLeast(0f)
      return Status.FAILURE
    }

    return when (val status = child.tick(context)) {
      Status.SUCCESS -> {
        remaining = seconds
        status
      }

      else -> status
    }
  }
}
