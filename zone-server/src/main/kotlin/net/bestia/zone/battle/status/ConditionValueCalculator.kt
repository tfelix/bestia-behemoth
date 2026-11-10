package net.bestia.zone.battle.status

import org.springframework.stereotype.Component
import kotlin.math.floor

/**
 * Pure calculations for the maximum condition-value pools (HP / Mana / Stamina) from a character's
 * level and effective primary attributes. See the game docs' status-value formulas:
 * https://docs.bestia-game.net/docs/mechanics/combat/statusvalues/
 *
 * These are the "formula-driven" pools of player-owned entities (master + player bestia). Mobs keep
 * the authored `Bestia.health` instead and are not run through this calculator.
 *
 * Simplified for the current milestone: there are no per-species base-value tables and no flat
 * pool modifiers yet, so the docs' `baseValue` collapses to a shared constant and `ModSum = 0`.
 * [iv] is an owned bestia's pool IV; a master passes none. The meaningful scaling the docs
 * describe is preserved: HP grows with level and VIT, Mana with level and INT, Stamina with level,
 * VIT, STR and WIL.
 */
@Component
class ConditionValueCalculator {

  /** [percent] is the docs' `HPModPerc` in percentage points: 10 means 10% more. */
  fun computeMaxHp(level: Int, vitality: Int, percent: Int = 0, iv: Int = 0): Int {
    val core = 15.0 + (BASE_VALUE_HP * 2.0 + iv) * level / 100.0 + level
    return floor(core * (1.0 + vitality * 0.01) * (1.0 + percent * 0.01)).toInt()
  }

  fun computeMaxMana(level: Int, intelligence: Int, percent: Int = 0, iv: Int = 0): Int {
    val core = 25.0 + (BASE_VALUE_MANA * 2.0 + iv) * level / 100.0 + level
    return floor(core * (1.0 + intelligence * 0.01) * (1.0 + percent * 0.01)).toInt()
  }

  fun computeMaxStamina(level: Int, vitality: Int, strength: Int, willpower: Int, iv: Int = 0): Int {
    val core = 20.0 + (BASE_VALUE_STAMINA * 2.0 + iv) * level / 100.0 + level
    return floor(core * (1.0 + vitality * 0.02) + strength / 5 + willpower / 5).toInt()
  }

  companion object {
    // Shared placeholder base values until per-species base-value tables land.
    private const val BASE_VALUE_HP = 40
    private const val BASE_VALUE_MANA = 20
    private const val BASE_VALUE_STAMINA = 30
  }
}
