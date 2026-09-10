package net.bestia.zone.ecs.spawn.townsfolk

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.ecs.AiThrottleable
import net.bestia.zone.ecs.battle.status.Invulnerable
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.environment.time.BestiaClock
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Brings people back out when their reason to be inside has passed.
 *
 * The other half of [IndoorRegistry]: going in destroys the entity, so nothing is left to decide when to
 * come out again and somebody outside has to. This is that somebody, and it is the whole system - the
 * person it rebuilds is derived from three indices and the settlement's seed, so there is no state here
 * beyond the registry's one line each.
 *
 * Coarse on purpose. A door opens on the hour rather than the tick, and a sweep over the people who are
 * indoors costs nothing next to the entities it is avoiding.
 */
@SpringComponent
@Order(83)
class IndoorEmergenceSystem(
  private val indoors: IndoorRegistry,
  private val spawner: TownsfolkEntitySpawner,
  private val clock: BestiaClock,
) : System {

  /** Nothing here is time-critical to a tick, and the answer only changes on the hour. */
  override val schedule: Schedule get() = Schedule.EverySeconds(SWEEP_SECONDS)

  /**
   * The markers put on somebody coming back out, for `AmbientSpawnerSystem`'s reason: the scheduler
   * decides what may share a wave from these sets alone, so a system that quietly writes a component it
   * did not declare looks like it conflicts with nobody. The components `BestiaEntitySpawner` puts on a
   * brand-new entity are not listed - no other system can hold an entity that did not exist yet.
   */
  override val writes: ComponentClassSet = setOf(Townsfolk::class, AiThrottleable::class, Invulnerable::class)

  override fun update(world: World, deltaTime: Float) {
    val hour = clock.now().hour

    for (record in indoors.dueOut(hour)) {
      // Removed first: an emergence that cannot find its settlement any more - the world regenerated, the
      // household went with a shrinking town - must not leave a record that is retried every sweep.
      indoors.leave(record.identity)

      val id = spawner.emerge(world, record.identity, record.door)
      if (id == null) {
        LOG.debug { "Nobody to bring out for ${TownsfolkIdentity.describe(record.identity)}; dropped" }
      }
    }
  }

  private companion object {
    /** A Bestia hour is twenty real minutes, so this is several sweeps to the hour and still nothing. */
    const val SWEEP_SECONDS = 20f

    val LOG = KotlinLogging.logger { }
  }
}
