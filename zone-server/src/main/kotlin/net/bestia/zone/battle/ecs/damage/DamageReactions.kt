package net.bestia.zone.battle.ecs.damage

import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * What the state of the two entities in a hit does around it: a barrier soaks it or a freeze shatters before it
 * lands, a lifesteal heals the one who dealt it after.
 */
interface DamageReactions {

  /** Returns how much of [amount] still reaches [targetId]'s health. */
  fun onDamageTaken(world: World, targetId: EntityId, amount: Int): Int

  fun onDamageDealt(world: World, sourceId: EntityId, landed: Int)

  companion object {
    val NONE = object : DamageReactions {
      override fun onDamageTaken(world: World, targetId: EntityId, amount: Int): Int {
        return amount
      }

      override fun onDamageDealt(world: World, sourceId: EntityId, landed: Int) {
      }
    }
  }
}
