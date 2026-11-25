package net.bestia.zone.casting.essence

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * The Ninefold Vixen's essence: every ninth spell is instant and free. A hidden status counts the spells, and a shown
 * one says the next is free.
 */
object NineTails {

  private const val SPELLS_BEFORE_FREE = 8

  fun isReady(world: World, casterId: EntityId): Boolean {
    return world.get(casterId, StatusEffects::class)?.hasEffect(StatusEffectId.NINE_TAILS_READY.id) == true
  }

  /** After a spell its wearer cast: the free one is spent, or the spell counts towards the next. */
  fun counted(world: World, casterId: EntityId, statusEffects: StatusEffectService) {
    if (isReady(world, casterId)) {
      statusEffects.removeEffect(world, casterId, StatusEffectId.NINE_TAILS_READY.id, RemovalReason.CANCELLED)
      return
    }

    statusEffects.applyEffect(world, casterId, StatusEffectId.NINE_TAILS_COUNT, level = 1)
    val counter = world.get(casterId, StatusEffects::class)?.activeEffects
      ?.firstOrNull { it.definitionId == StatusEffectId.NINE_TAILS_COUNT.id } ?: return
    counter.count += 1

    if (counter.count >= SPELLS_BEFORE_FREE) {
      statusEffects.removeEffect(world, casterId, StatusEffectId.NINE_TAILS_COUNT.id, RemovalReason.CANCELLED)
      statusEffects.applyEffect(world, casterId, StatusEffectId.NINE_TAILS_READY, level = 1)
    }
  }
}
