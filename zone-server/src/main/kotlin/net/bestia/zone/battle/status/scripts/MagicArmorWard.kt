package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.damage.HitForm
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 217 (`MAGIC_ARMOR`): `-10%/lv` of every blow and spell for 5 min. Each hit costs 2% of
 * the bearer's current mana, and the ward breaks once mana falls below 20%.
 */
@Component
class MagicArmorWard : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return DURATION_SECONDS
  }

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    context.addDamageTaken(TAKEN_PERCENT_PER_LEVEL * effect.level.coerceIn(1, MAX_LEVEL), anyHit = true)
  }

  override fun onDamageTaken(context: StatusEffectHookContext, hit: IncomingDamage.DamageAmount): Int {
    if (hit.amount <= 0 || hit.kind.form == HitForm.OTHER) return hit.amount
    val mana = context.world.get(context.hostId, Mana::class) ?: return hit.amount

    // At least one point, so a nearly empty pool still runs dry instead of holding the ward forever.
    mana.current -= (mana.current * MANA_PERCENT_PER_HIT / 100).coerceAtLeast(1)
    if (mana.current * 100 < mana.max * BREAKS_BELOW_MANA_PERCENT) {
      context.removeSelf()
    }

    return hit.amount
  }

  private companion object {
    const val DURATION_SECONDS = 300.0
    const val TAKEN_PERCENT_PER_LEVEL = 10
    const val MAX_LEVEL = 3
    const val MANA_PERCENT_PER_HIT = 2
    const val BREAKS_BELOW_MANA_PERCENT = 20
  }
}
