package net.bestia.zone.stronghold.ecs

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.prop.ecs.construction.ConstructionSite
import net.bestia.zone.stronghold.EmpyriteSites
import org.springframework.stereotype.Component as SpringComponent

/** Lets go of Empyrite sites that were destroyed or left unworked too long. */
@SpringComponent
class StrongholdUpkeepSystem(
  private val sites: EmpyriteSites,
) : System {

  override val schedule: Schedule = Schedule.EverySeconds(60f)
  override val phase = Phase.UPKEEP

  override val reads: ComponentClassSet = setOf(ConstructionSite::class)
  override val writes: ComponentClassSet = setOf(EmpyriteSeedSprout::class)

  override fun update(world: World, deltaTime: Float) {
    sites.expire(world)
  }
}
