package net.bestia.zone.battle.damage

import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.util.EntityId

/**
 * Weighs a hit by both sides' [DamageModifiers] at the moment it is staged on its target. Every path that hurts
 * something - a swing, a skill, a patch of fire - goes through here, so the number the client shows is the number
 * that lands.
 */
object DamageLanding {

  /** What [weigh] reads, for the systems that call it. */
  val READS: ComponentClassSet = setOf(CombatStats::class, Nature::class, Master::class)

  fun weigh(world: World, attackerId: EntityId?, targetId: EntityId, kind: HitKind, amount: Int): Int {
    if (kind.form == HitForm.OTHER) {
      return amount
    }
    val defender = sideOf(world, targetId) ?: return amount

    return DamageModifiers.weigh(amount, kind, attackerId?.let { sideOf(world, it) }, defender)
  }

  private fun sideOf(world: World, entityId: EntityId): DamageModifiers.Side? {
    val combat = world.get(entityId, CombatStats::class)?.values ?: return null
    val nature = world.get(entityId, Nature::class)

    return DamageModifiers.Side(
      modifiers = combat.damageModifiers,
      kind = nature?.kind ?: masterKindOf(world, entityId),
      element = combat.elementOverride ?: nature?.element ?: Element.NORMAL,
      boss = nature?.boss == true,
    )
  }

  /** A master has no species. RO counts a player as a demi-human, which is our humanoid. */
  private fun masterKindOf(world: World, entityId: EntityId): BestiaKind? {
    return if (world.has(entityId, Master::class)) BestiaKind.HUMANOID else null
  }
}
