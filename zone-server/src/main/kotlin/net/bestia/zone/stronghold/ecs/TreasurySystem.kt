package net.bestia.zone.stronghold.ecs

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.stronghold.Treasury
import org.springframework.stereotype.Component as SpringComponent

/** Makes each Stronghold's daily treasure drop once it is due. */
@SpringComponent
class TreasurySystem(
  private val treasury: Treasury,
) : System {

  override val schedule: Schedule = Schedule.EverySeconds(60f)
  override val phase = Phase.UPKEEP

  override val reads: ComponentClassSet = emptySet()
  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    treasury.makeDue()
  }
}
