package net.bestia.zone.stronghold.ecs

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.stronghold.Watchers
import net.bestia.zone.weather.EnvironmentalExposureSystem
import org.springframework.stereotype.Component as SpringComponent

/** Notes which watchers fell, and calls them back once their time is up. */
@SpringComponent
class WatcherSystem(
  private val watchers: Watchers,
) : System {

  override val schedule: Schedule = Schedule.EverySeconds(1f)
  override val phase = Phase.UPKEEP
  // Exposure can kill a watcher too; it should be counted as fallen on the same tick.
  override val after = setOf(EnvironmentalExposureSystem::class)

  override val reads: ComponentClassSet = setOf(Dead::class)
  override val writes: ComponentClassSet = setOf(Watcher::class)

  override fun update(world: World, deltaTime: Float) {
    watchers.watch(world)
  }
}
