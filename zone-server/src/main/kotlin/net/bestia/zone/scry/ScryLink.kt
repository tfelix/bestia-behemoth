package net.bestia.zone.scry

import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 251 (`SCRY_EYE`): the spell itself, on the far eye. It carries the cast's Potency, so a
 * Dispel on the eye weighs against it and, when it lifts it, ends the sight.
 */
@Component
class ScryLink(private val ending: ScryEnding) : StatusEffectScript {

  override val isPersisted: Boolean = false

  override val isCancellable: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return ScryService.SECONDS
  }

  override fun onRemove(context: StatusEffectHookContext, reason: RemovalReason) {
    ending.fromEye(context)
  }
}
