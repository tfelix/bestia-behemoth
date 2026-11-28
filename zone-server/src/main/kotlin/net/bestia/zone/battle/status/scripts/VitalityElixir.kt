package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusValueRecalcContext
import org.springframework.stereotype.Component

/** `status_effects.yml` `VITALITY_ELIXIR`: see [AttributeElixir]. */
@Component
class VitalityElixir : AttributeElixir() {

  override fun raise(context: StatusValueRecalcContext, bonus: Int) {
    context.vitality += bonus
  }
}
