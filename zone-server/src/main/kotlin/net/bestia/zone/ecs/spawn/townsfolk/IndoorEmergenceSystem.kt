package net.bestia.zone.ecs.spawn.townsfolk

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.environment.time.BestiaClock
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Opens the door when somebody's reason to be inside has passed.
 *
 * It opens the door and nothing else. Whether anybody is then *built* is
 * [TownsfolkResidencySystem]'s to decide, because that is the one thing that knows whether a player is
 * near enough for it to matter - and two systems both materialising the same person would put two of
 * them in the street.
 *
 * So there is no spawning here, and a town that wakes up with nobody watching wakes up for free: every
 * record is dropped, no entity is created, and the first player to walk in gets a street full of people
 * from the residency sweep.
 *
 * Coarse on purpose. A door opens on the hour rather than the tick.
 */
@SpringComponent
@Order(83)
class IndoorEmergenceSystem(
  private val indoors: IndoorRegistry,
  private val clock: BestiaClock,
) : System {

  /** Nothing here is time-critical to a tick, and the answer only changes on the hour. */
  override val schedule: Schedule get() = Schedule.EverySeconds(SWEEP_SECONDS)

  override fun update(world: World, deltaTime: Float) {
    val hour = clock.now().hour

    for (record in indoors.dueOut(hour)) {
      indoors.leave(record.identity)
      LOG.trace { "${TownsfolkIdentity.describe(record.identity)} may come out" }
    }
  }

  private companion object {
    /** A Bestia hour is twenty real minutes, so this is several sweeps to the hour and still nothing. */
    const val SWEEP_SECONDS = 20f

    val LOG = KotlinLogging.logger { }
  }
}
