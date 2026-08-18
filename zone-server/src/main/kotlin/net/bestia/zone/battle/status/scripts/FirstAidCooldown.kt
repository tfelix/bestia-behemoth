package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 8 (`FIRST_AID_COOLDOWN`) - bookkeeping, never shown, no stat effect.
 *
 * Marks a bestia as having had First Aid recently, which is how
 * [net.bestia.zone.battle.skill.scripts.FirstAid] enforces the once-a-minute limit its description
 * promises. On the target rather than on the caster, deliberately: the limit is per bestia, so two
 * masters cannot take turns topping the same one up.
 */
@Component
class FirstAidCooldown : StatusEffectScript {

  /** Re-applying must not extend an existing lockout, or a refused cast would punish the target. */
  override val stackBehavior: StackBehavior = StackBehavior.IGNORE_IF_PRESENT

  override fun durationSeconds(level: Int): Double = LOCKOUT_SECONDS

  private companion object {
    const val LOCKOUT_SECONDS = 60.0
  }
}
