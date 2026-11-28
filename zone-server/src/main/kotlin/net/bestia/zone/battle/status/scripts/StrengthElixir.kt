package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusValueRecalcContext
import org.springframework.stereotype.Component

/** `status_effects.yml` `STRENGTH_ELIXIR`: see [AttributeElixir]. */
@Component
class StrengthElixir : AttributeElixir() {

  override fun raise(context: StatusValueRecalcContext, bonus: Int) {
    context.strength += bonus
  }
}
