package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusValueRecalcContext
import org.springframework.stereotype.Component

/** `status_effects.yml` `AGILITY_ELIXIR`: see [AttributeElixir]. */
@Component
class AgilityElixir : AttributeElixir() {

  override fun raise(context: StatusValueRecalcContext, bonus: Int) {
    context.agility += bonus
  }
}
