package net.bestia.zone.battle.mark

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.scripts.HuntersMarked
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** What a Hunter's Mark on a prey gives the hunter who put it there. */
object HuntersMarks {

  val READS: ComponentClassSet = setOf(StatusEffects::class)

  /** The level of the mark [hunterId] put on [preyId]; 0 when it has none from them. */
  fun levelOn(world: World, hunterId: EntityId, preyId: EntityId): Int {
    val mark = world.get(preyId, StatusEffects::class)?.activeEffects
      ?.firstOrNull { it.definitionId == StatusEffectId.HUNTERS_MARK.id && it.sourceEntityId == hunterId }

    return mark?.level ?: 0
  }

  /** Percent more chance for [attackerId] to crit [targetId], when it marked it. */
  fun critPercentAgainst(world: World, attackerId: EntityId, targetId: EntityId): Int {
    val level = levelOn(world, attackerId, targetId)

    return if (level > 0) HuntersMarked.critPercent(level) else 0
  }
}
