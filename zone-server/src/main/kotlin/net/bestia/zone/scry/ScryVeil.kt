package net.bestia.zone.scry

import net.bestia.zone.aoi.EntityVisibility
import net.bestia.zone.battle.status.scripts.Concealment
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 252 (`SCRY_VEIL`): hides the far eye like Hide hides a body, so only Ruwach uncovers it.
 * It outlasts the sight; the eye goes with the sight.
 */
@Component
class ScryVeil(visibility: EntityVisibility) : Concealment(visibility) {

  override val isCancellable: Boolean = false

  override val showsCountdown: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return Double.POSITIVE_INFINITY
  }
}
