package net.bestia.zone.ai.rumour

import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.stereotype.Component as SpringComponent

/**
 * Drops news nobody would bring up any more.
 *
 * Without this the ledger only ever grows. A conversation would still look right - what a town knows
 * filters spent news out on the way past - so the symptom is not a wrong line to a player but a table
 * that never stops filling, which is exactly the kind of thing found a year later and not in a test.
 *
 * Touches no components at all, so it conflicts with nothing and the scheduler is free to run it
 * beside anything. That is safe here for the reason it usually is not: the registry it writes to is
 * tick-thread state like every other registry of its kind, and the scheduler runs systems on the tick.
 *
 * Every few minutes rather than every tick. Expiry is measured in Bestia days, so even at the fastest
 * clock nothing can fall due inside one sweep interval, and a scan of the settlements holding news is
 * wasted work on all but a handful of ticks.
 */
@SpringComponent
class RumourExpirySystem(
  private val rumours: RumourService,
) : System {

  override val schedule: Schedule = Schedule.EverySeconds(SWEEP_SECONDS)

  override fun update(world: World, deltaTime: Float) {
    rumours.forgetExpired()
  }

  private companion object {
    /** Three minutes, which is `Schedule`'s own worked example and far finer than a day. */
    const val SWEEP_SECONDS = 180f
  }
}
