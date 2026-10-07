package net.bestia.zone.battle.damage

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.Invulnerable
import net.bestia.zone.battle.status.HarmShield
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * The one answer to "may this harm land". Every path that hurts an entity asks here, so a new kind of
 * protection is one change in this object rather than one per path.
 */
object DamageGate {

  /** Folded into the reads of every system that asks, so the scheduler sees what the gate looks at. */
  val READS: ComponentClassSet = setOf(Invulnerable::class, StatusEffects::class)

  /**
   * Asked where damage is staged, not only where it is drained: by the drain the client has already been
   * shown a number it would never see subtracted.
   */
  fun verdict(world: World, sourceId: EntityId?, targetId: EntityId): Verdict {
    if (isImmune(world, targetId)) {
      return Verdict.IMMUNE
    }

    return Verdict.ADMITTED
  }

  /** For the paths that have no source to weigh: the drain, the weather, a trap. */
  fun isImmune(world: World, targetId: EntityId): Boolean {
    return world.has(targetId, Invulnerable::class) ||
        world.get(targetId, StatusEffects::class)?.hasShield(HarmShield.ALL) == true
  }

  enum class Verdict {
    ADMITTED,
    IMMUNE,
  }
}
