package net.bestia.zone.settlement.ecs

import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.weather.EnvironmentalExposureSystem
import net.bestia.zone.prop.ecs.construction.ConstructionSite
import net.bestia.zone.settlement.TownService
import org.springframework.stereotype.Component as SpringComponent

/** Recounts what each town has built, and lets go of fallen towns and idle sign sites whose time is up. */
@SpringComponent
class TownUpkeepSystem(
  private val towns: TownService,
) : System {

  override val schedule: Schedule = Schedule.EverySeconds(10f)
  override val phase = Phase.UPKEEP

  // Late in the upkeep, after the other systems that touch health. GuildMemberBeaconSystem declares its own order.
  override val after = setOf(EnvironmentalExposureSystem::class)

  override val reads: ComponentClassSet = setOf(ConstructionSite::class, CitySign::class)
  override val writes: ComponentClassSet = setOf(Health::class, CombatStats::class)

  private var runs = 0

  override fun update(world: World, deltaTime: Float) {
    towns.regrow(world)

    if (runs++ % EXPIRE_EVERY_RUNS == 0) {
      towns.expire(world)
    }
  }

  private companion object {
    const val EXPIRE_EVERY_RUNS = 6
  }
}
