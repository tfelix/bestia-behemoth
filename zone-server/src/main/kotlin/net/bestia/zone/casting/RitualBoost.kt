package net.bestia.zone.casting

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.potency.Potency
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * What a ritual charge adds to the one skill that spends it. Each script says how it reacts, see the spell
 * potency page's "How Every Skill Reacts to a Charge": a contest takes the higher Potency on its own, a success
 * roll adds [successBonusPercent], and damage, healing, shields and buff length may add [surgePercent].
 */
data class RitualBoost(
  val potencyBonus: Int,
  val successBonusPercent: Int,
) {

  val surgePercent: Int
    get() = Potency.surgePercent(potencyBonus)

  /** Scales [value] by the surge, for a skill whose damage, healing or duration takes it. */
  fun surged(value: Int): Int = value * (100 + surgePercent) / 100

  companion object {
    /** The charge the caster holds now, or null. Reading it does not spend it. */
    fun heldBy(world: World, casterId: EntityId): RitualBoost? {
      val charge = world.get(casterId, StatusEffects::class)?.activeEffects
        ?.firstOrNull { it.definitionId == StatusEffectId.RITUAL_CHARGE.id }
        ?: return null

      return RitualBoost(potencyBonus = charge.amount, successBonusPercent = charge.count)
    }
  }
}
