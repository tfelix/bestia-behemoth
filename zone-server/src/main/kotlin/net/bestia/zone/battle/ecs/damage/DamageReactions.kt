package net.bestia.zone.battle.ecs.damage

import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** What an entity's own state does to a hit before it lands: a barrier soaks it, a freeze shatters. */
fun interface DamageReactions {

  /** Returns how much of [amount] still reaches [targetId]'s health. */
  fun onDamageTaken(world: World, targetId: EntityId, amount: Int): Int

  companion object {
    val NONE = DamageReactions { _, _, amount -> amount }
  }
}
