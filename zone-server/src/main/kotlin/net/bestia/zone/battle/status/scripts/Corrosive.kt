package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/** A hide that eats the weapons hitting it, see `AttackExecutionService`. */
@Component
class Corrosive : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return 60.0
  }

  companion object {
    /** Durability a melee weapon loses per blow on the bearer. */
    const val WEAR_PER_HIT = 2
  }
}
