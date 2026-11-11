package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.damage.HitForm
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 212 (`KYRIE_ELEISON`): the Priest's ward. It soaks up to `10%/lv` of the bearer's max HP
 * from each of its next `1/lv` blows or spells, for 60 s. A burn or a poison tick is no hit and passes through.
 * A bestia's Kyrie is [Barrier], a pool instead.
 *
 * The soak per hit lives in the instance's `amount`, the hits left in its `count`.
 */
@Component
class KyrieEleisonWard : StatusEffectScript {

  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double = DURATION_SECONDS

  override fun onApply(context: StatusEffectHookContext) {
    val maxHealth = context.world.get(context.hostId, Health::class)?.max ?: 0
    val level = context.effect.level.coerceIn(1, MAX_LEVEL)

    context.effect.amount = maxHealth * SOAK_PERCENT_PER_LEVEL * level / 100
    context.effect.count = level
  }

  override fun onDamageTaken(context: StatusEffectHookContext, hit: IncomingDamage.DamageAmount): Int {
    if (hit.amount <= 0 || hit.kind.form == HitForm.OTHER) return hit.amount

    context.effect.count -= 1
    if (context.effect.count <= 0) {
      context.removeSelf()
    }

    return (hit.amount - context.effect.amount).coerceAtLeast(0)
  }

  private companion object {
    const val DURATION_SECONDS = 60.0
    const val SOAK_PERCENT_PER_LEVEL = 10
    const val MAX_LEVEL = 3
  }
}
