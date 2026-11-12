package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 223 (`EQUIPMENT_COATING`): for 10 min every worn piece wears down less, and Strip
 * Equipment fails against the bearer outright. The wear is cut where it happens, see `EquipmentWear`.
 */
@Component
class GearCoating : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return DURATION_SECONDS
  }

  companion object {
    private const val DURATION_SECONDS = 600.0
    private const val MAX_LEVEL = 3

    /** Equipment Coating's table: `-20%` wear at Lv.1, 40% more each level. */
    fun wearCutPercent(level: Int): Int {
      return 20 + 40 * (level.coerceIn(1, MAX_LEVEL) - 1)
    }

    /** How much of a piece's wear still lands on the bearer of [effects], in percent. */
    fun wearKeptPercent(effects: StatusEffects?): Int {
      val coating = effects?.activeEffects?.firstOrNull { it.definitionId == StatusEffectId.EQUIPMENT_COATING.id }
        ?: return 100

      return 100 - wearCutPercent(coating.level)
    }
  }
}
