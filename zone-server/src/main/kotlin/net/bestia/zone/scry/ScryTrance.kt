package net.bestia.zone.scry

import net.bestia.zone.battle.status.EntityAction
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 250 (`SCRYING`): the Sage's body while its sight is away. Whatever the body does, and any
 * hit it takes, calls the sight back. Recasting Scry cancels it, which is the way back to the body.
 */
@Component
class ScryTrance(private val ending: ScryEnding) : StatusEffectScript {

  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return ScryService.SECONDS
  }

  override fun onActed(context: StatusEffectHookContext, action: EntityAction) {
    context.removeSelf()
  }

  override fun onDamageTaken(context: StatusEffectHookContext, amount: Int): Int {
    // Deferred: the hit is being weighed against every effect on the body right now.
    if (amount > 0) context.world.defer { context.removeSelf() }

    return amount
  }

  override fun onRemove(context: StatusEffectHookContext, reason: RemovalReason) {
    ending.fromTrance(context)
  }
}
