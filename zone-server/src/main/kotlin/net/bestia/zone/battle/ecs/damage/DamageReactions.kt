package net.bestia.zone.battle.ecs.damage

import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * What the state of the two entities in a hit does around it: a barrier soaks it or a freeze shatters before it
 * lands, a lifesteal heals the one who dealt it after.
 */
interface DamageReactions {

  /** Returns how much of [hit] still reaches [targetId]'s health. */
  fun onDamageTaken(world: World, targetId: EntityId, hit: IncomingDamage.DamageAmount): Int

  fun onDamageDealt(world: World, sourceId: EntityId, landed: Int)

  /** Whether [targetId] lives through damage that would kill it, at 1 HP. Asked only when it would die. */
  fun survivesLethalDamage(world: World, targetId: EntityId): Boolean {
    return false
  }

  companion object {
    val NONE = object : DamageReactions {
      override fun onDamageTaken(world: World, targetId: EntityId, hit: IncomingDamage.DamageAmount): Int {
        return hit.amount
      }

      override fun onDamageDealt(world: World, sourceId: EntityId, landed: Int) {
      }
    }
  }
}
