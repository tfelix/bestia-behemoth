package net.bestia.zone.worldevent.alpha

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.observe.KillFact
import net.bestia.zone.entity.observe.KillObserver
import net.bestia.zone.world.time.BestiaClock
import org.springframework.stereotype.Component

/** A den is hunted when masters kill one of its pack; a bestia that died of anything else does not count. */
@Component
class DenHuntObserver(
  private val hunting: DenHunting,
  private val clock: BestiaClock,
) : KillObserver {

  override val reads: ComponentClassSet = emptySet()
  override val writes: ComponentClassSet = emptySet()

  override fun onKill(world: World, kill: KillFact) {
    val den = kill.denFeatureId ?: return
    if (kill.creditedMasters.isEmpty()) return
    hunting.hunted(den, clock.now().absoluteSecond)
  }
}
