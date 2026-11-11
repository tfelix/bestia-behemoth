package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.damage.HitForm
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 210 (`LEX_AETERNA`): the next blow or spell to land on the bearer within 20 s counts
 * twice, then the mark is gone. A burn or a poison tick is no hit and leaves the mark alone.
 */
@Component
class LexAeternaMark : StatusEffectScript {

  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double = DURATION_SECONDS

  override fun onDamageTaken(context: StatusEffectHookContext, hit: IncomingDamage.DamageAmount): Int {
    if (hit.amount <= 0 || hit.kind.form == HitForm.OTHER) return hit.amount

    context.removeSelf()

    return hit.amount * 2
  }

  private companion object {
    const val DURATION_SECONDS = 20.0
  }
}
