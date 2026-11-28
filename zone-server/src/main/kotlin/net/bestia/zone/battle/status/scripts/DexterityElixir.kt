package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusValueRecalcContext
import org.springframework.stereotype.Component

/** `status_effects.yml` `DEXTERITY_ELIXIR`: see [AttributeElixir]. */
@Component
class DexterityElixir : AttributeElixir() {

  override fun raise(context: StatusValueRecalcContext, bonus: Int) {
    context.dexterity += bonus
  }
}
