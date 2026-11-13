package net.bestia.zone.item.mining.ecs

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.mining.Blasts
import org.springframework.stereotype.Component

/** Burns down the fuses of [Blasts]. It touches no component: a blast reaches the world as a carve. */
@Component
class BlastFuseSystem(
  private val blasts: Blasts,
) : System {
  override val phase = Phase.ACTIONS
  override val reads: ComponentClassSet = emptySet()
  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    blasts.burn(deltaTime)
  }
}
