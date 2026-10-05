package net.bestia.zone.ecs.persistence

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Saves each persistent entity once per `persistence.interval-ms`, a slice every second. On the tick, so a
 * snapshot needs no lease and no tick ever snapshots the whole population at once.
 */
@SpringComponent
@Order(91)
class EntityPersistenceSystem(
  private val persistence: EntityPersistenceService,
  config: EntityPersistenceConfig,
) : System {

  override val schedule: Schedule = Schedule.EverySeconds(SWEEP_SECONDS)

  override val reads: ComponentClassSet = setOf(Persistent::class) + PersistAndRemoveSystem.SNAPSHOT_READS

  private val sweepsPerInterval = (config.intervalMs / 1000f / SWEEP_SECONDS).toLong().coerceAtLeast(1)

  private var sweep = 0L

  override fun update(world: World, deltaTime: Float) {
    persistence.syncDue(world, sweep++, sweepsPerInterval)
  }

  private companion object {
    const val SWEEP_SECONDS = 1f
  }
}
