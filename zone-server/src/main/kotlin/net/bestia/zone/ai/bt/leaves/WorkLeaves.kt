package net.bestia.zone.ai.bt.leaves

import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.BtNode
import net.bestia.zone.ai.core.behavior.Status
import net.bestia.zone.ecs.movement.Path

/**
 * Turns out one piece of work every [secondsPerUnit], for as long as [canBegin] allows.
 *
 * The unit is the point. Work that only counted when the whole shift ended would be lost every time
 * anything more urgent came up, and hunger alone interrupts a townsperson twice a day - so what is
 * finished is banked as it is finished, and a baker who breaks for lunch keeps the loaves already baked.
 *
 * [canBegin] is asked before each piece rather than every tick, which is both cheaper and truer: you
 * find out the flour has run out when you reach for it. FAILURE then, because a shift whose work cannot
 * be done is over, and `UntilHour` reads child failure as exactly that.
 *
 * Dropping the [Path] on the first tick is [StandStill]'s guard, for [StandStill]'s reason.
 */
class Labour(
  private val secondsPerUnit: Float,
  private val canBegin: (BtContext) -> Boolean,
  private val onFinished: (BtContext) -> Unit,
) : BtNode {

  init {
    require(secondsPerUnit > 0f) { "Labour requires secondsPerUnit > 0, got $secondsPerUnit" }
  }

  private var elapsed = 0f
  private var begun = false
  private var dropped = false

  override fun tick(context: BtContext): Status {
    if (!dropped) {
      context.world.remove(context.entityId, Path::class)
      dropped = true
    }

    if (!begun) {
      if (!canBegin(context)) return Status.FAILURE
      begun = true
    }

    elapsed += context.deltaTime
    if (elapsed < secondsPerUnit) return Status.RUNNING

    elapsed -= secondsPerUnit
    begun = false
    onFinished(context)

    return Status.RUNNING
  }

  override fun toString(): String = "Labour(${secondsPerUnit}s/unit)"
}
