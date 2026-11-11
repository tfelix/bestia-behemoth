package net.bestia.zone.casting

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * Magic Rod at the moment a spell lands: a single-target spell at a bearer of [StatusEffectId.MAGIC_ROD] is caught
 * whole, damage and curses alike. The bearer gets 50% of the spell's mana cost back, 25% more per level, and the
 * rod is spent. Area spells wash over it, so only the cast's own target is asked.
 */
object MagicRodCatch {

  /** True when the cast was caught and must not resolve. */
  fun catches(world: World, targetId: EntityId, manaCost: Int, statusEffects: StatusEffectService): Boolean {
    val rod = world.get(targetId, StatusEffects::class)?.activeEffects
      ?.firstOrNull { it.definitionId == StatusEffectId.MAGIC_ROD.id }
      ?: return false

    world.get(targetId, Mana::class)?.let { it.current += manaCost * refundPercent(rod.level) / 100 }
    statusEffects.removeEffect(world, targetId, StatusEffectId.MAGIC_ROD.id, RemovalReason.CANCELLED)

    return true
  }

  fun refundPercent(level: Int): Int = BASE_PERCENT + PERCENT_PER_LEVEL * (level.coerceIn(1, MAX_LEVEL) - 1)

  private const val BASE_PERCENT = 50
  private const val PERCENT_PER_LEVEL = 25
  private const val MAX_LEVEL = 3
}
