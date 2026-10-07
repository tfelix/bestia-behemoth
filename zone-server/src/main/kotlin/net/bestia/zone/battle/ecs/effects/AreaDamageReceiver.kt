package net.bestia.zone.battle.ecs.effects

import net.bestia.zone.battle.Element
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * Takes area damage for victims without `Health`, such as items on the ground. A port, because the slices
 * that own such victims sit above `battle`.
 */
interface AreaDamageReceiver {

  /** What [receive] touches, which [AreaEffectSystem] declares as its own. */
  val reads: ComponentClassSet
  val writes: ComponentClassSet

  /** @return true if [victimId] is one of its victims, so the effect must not treat it as a combat target. */
  fun receive(world: World, victimId: EntityId, damage: Int, element: Element): Boolean

  companion object {
    val NONE = object : AreaDamageReceiver {
      override val reads: ComponentClassSet = emptySet()
      override val writes: ComponentClassSet = emptySet()

      override fun receive(world: World, victimId: EntityId, damage: Int, element: Element): Boolean {
        return false
      }
    }
  }
}
