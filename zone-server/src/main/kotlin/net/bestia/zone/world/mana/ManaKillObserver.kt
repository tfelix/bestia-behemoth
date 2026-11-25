package net.bestia.zone.world.mana

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.observe.KillFact
import net.bestia.zone.entity.observe.KillObserver
import org.springframework.stereotype.Component

/** A slain bestia releases the mana it carried where it fell. A captured one keeps it. */
@Component
class ManaKillObserver(
  private val emission: ManaEmission,
) : KillObserver {

  override val reads: ComponentClassSet = emptySet()
  override val writes: ComponentClassSet = emptySet()

  override fun onKill(world: World, kill: KillFact) {
    emission.onKill(kill.position, kill.level, kill.boss)
  }
}
