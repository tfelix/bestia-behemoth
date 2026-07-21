package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Registered under `status_effects.yml` id 5 (`BLESSING`), applied by the BLESSING skill (see
 * `net.bestia.zone.battle.skill.scripts.Blessing` - a distinct class registered in the skill
 * script registry; named differently here since Spring's default bean name is the decapitalised
 * simple class name regardless of package, and both scripts being called plain `Blessing` collided).
 *
 * `skills.yml`'s description talks about boosting STR/DEX/INT/HIT, but only speed is modeled as a
 * buffable stat so far - this reuses the same speed-multiplier shape as `Swiftness` until a
 * broader stat-modifier is needed.
 */
@Component
class BlessingStatusEffect : StatusEffectScript {

  override fun durationSeconds(level: Int): Double = 4.0

  override fun apply(context: StatusValueRecalcContext, level: Int, sourceEntityId: EntityId?) {
    context.speed *= 1.1f
  }
}
