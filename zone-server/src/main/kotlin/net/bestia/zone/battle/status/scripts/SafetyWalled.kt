package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.damage.HitForm
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 219 (`SAFETY_WALL`): standing on a Safety Wall. Each melee or ranged blow is stopped whole
 * and spends one of the wall's hits; the last one breaks the wall. A spell passes. Its zone refreshes it like
 * [Sheltered]'s.
 */
@Component
class SafetyWalled : StatusEffectScript {

  override val isPersisted: Boolean = false
  override val showsCountdown: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return 1.0
  }

  override fun onDamageTaken(context: StatusEffectHookContext, hit: IncomingDamage.DamageAmount): Int {
    if (hit.amount <= 0 || hit.kind.form != HitForm.PHYSICAL) return hit.amount
    val wall = context.effect.sourceEntityId?.let { context.world.get(it, AreaEffect::class) }
    if (wall == null || wall.hitsLeft <= 0) {
      // A refresh keeps the old source, so this lets a wall next to a spent one claim the bearer on its next tick.
      context.removeSelf()
      return hit.amount
    }

    wall.hitsLeft -= 1
    if (wall.hitsLeft == 0) {
      // Burnt out on the area's next tick, like a wall whose time ran out.
      wall.remainingTicks = 0
      context.removeSelf()
    }

    return 0
  }
}
