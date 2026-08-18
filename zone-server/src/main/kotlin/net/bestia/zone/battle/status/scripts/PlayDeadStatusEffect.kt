package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 7 (`PLAY_DEAD`), applied by the skill of the same name.
 *
 * Changes no status value at all - what it does is make `PerceptionSystem` look past whoever carries
 * it, both when picking a target to hunt and when remembering who hurt it. That is the whole effect,
 * and it lives there because being noticed is a perception question rather than a stat.
 *
 * The design docs call this a toggle. It lands as a fixed duration instead: a
 * [net.bestia.zone.battle.skill.SkillStrategy] returns a
 * [net.bestia.zone.battle.damage.Damage] and has no way to *remove* an effect, so switching it back
 * off needs a result type that means "take this away" - a separate piece of work.
 */
@Component
class PlayDeadStatusEffect : StatusEffectScript {

  /** Re-casting extends the act rather than stacking a second copy of it. */
  override val stackBehavior: StackBehavior = StackBehavior.REFRESH_DURATION

  override fun durationSeconds(level: Int): Double = DURATION_SECONDS

  private companion object {
    /** Long enough to let a hunted novice break off and walk away, short enough not to be a hiding place. */
    const val DURATION_SECONDS = 30.0
  }
}
