package net.bestia.zone.battle.status.scripts

import net.bestia.zone.aoi.EntityVisibility
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.status.EntityAction
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 221 (`HIDING`), the Assassin's Hide: unseen until the bearer walks, attacks or casts. Area
 * attacks pass over it, as over a bestia's [Hidden], see `AreaEffectSystem`. A bestia's Burrow is [Hidden].
 */
@Component
class Hiding(visibility: EntityVisibility) : Concealment(visibility) {

  override val revealedBy: Set<EntityAction> = setOf(EntityAction.MOVED, EntityAction.ATTACKED, EntityAction.CAST)

  override val showsCountdown: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return Double.POSITIVE_INFINITY
  }

  override fun upkeep(effect: ActiveStatusEffect, mana: Mana): Int {
    return drainOfTenths(effect, drainTenthsPerSecond(effect.level))
  }

  companion object {
    private const val MAX_LEVEL = 5

    /** Hide's table: 3 mana a second at Lv.1, half a point less each level. */
    fun drainTenthsPerSecond(level: Int): Int {
      return 30 - 5 * (level.coerceIn(1, MAX_LEVEL) - 1)
    }

    /** How much of a detector's reach Hide takes away: `10%/lv`. Ruwach ignores it. */
    fun detectionResistancePercent(level: Int): Int {
      return 10 * level.coerceIn(1, MAX_LEVEL)
    }
  }
}
