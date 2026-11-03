package net.bestia.zone.battle.attack

import net.bestia.zone.battle.ammo.AmmoSupply
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * The basic attack an entity has with what it holds. A ranged weapon shoots the next fitting ammo from the
 * quiver; anything else leaves the [fallback][resolve] the caller brings: a swing for a player, the species'
 * own attack for a bestia's AI.
 */
@Component
class BasicAttackResolver(
  private val ammoSupply: AmmoSupply
) {

  /** Null when a ranged weapon is held but the quiver has nothing left it can fire. */
  fun resolve(world: World, entityId: EntityId, fallback: BattleAttack): BattleAttack? {
    val weapon = world.get(entityId, CombatStats::class)?.values?.weapon?.ranged ?: return fallback
    val ammunition = ammoSupply.nextFor(world, entityId, weapon) ?: return null

    return BattleAttack.shotWith(weapon, ammunition, fallback.baseAttackMotionMs)
  }
}
