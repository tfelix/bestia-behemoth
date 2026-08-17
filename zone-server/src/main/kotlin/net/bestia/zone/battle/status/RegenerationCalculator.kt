package net.bestia.zone.battle.status

import org.springframework.stereotype.Component
import kotlin.math.max

/**
 * Pure calculations for how much a condition-value pool (HP / Mana / Stamina) refills per
 * regeneration tick. See the game docs' recovery formulas:
 * https://docs.bestia-game.net/docs/mechanics/statusvalues/
 *
 * A sibling of [ConditionValueCalculator] rather than more methods on it: pool *size* and pool
 * *refill rate* are different questions with different inputs, and each carries its own set of
 * milestone simplifications.
 *
 * The tick intervals the docs pair with these amounts (6 s / 8 s / 10 s) live on the systems that
 * call this, as their `Schedule.EverySeconds`.
 *
 * Simplified for the current milestone: the docs' `HPRMod` / `MPRMod` / `STARMod` are all 0
 * (`ModPerc = 1`) because nothing writes a regeneration modifier yet - one would arrive through
 * [StatusValueRecalcContext], the same way equipment and effect scripts reach every other derived
 * value. The docs' "doubles while resting" is likewise unmodelled, because there is no resting
 * state; the nearest thing today is the `InCombat` gate the calling systems already apply. Both are
 * left out entirely rather than taken as parameters no caller passes, so nothing here is shipped
 * dead.
 *
 * Integer division throughout, deliberately: it truncates toward zero and every input is
 * non-negative, so `/` **is** the docs' `floor`. Doing this in [Double] and rounding at the end is
 * what produced the operator-precedence bug this class replaced (`max * vit / 99.0 + 2.0 / 100.0`,
 * which parses as `(max * vit / 99) + 0.02` and is ~19x the documented rate at level 100).
 */
@Component
class RegenerationCalculator {

  /** `BaseHPR = max(1, floor(MaxHP / 200))`, `HPR = BaseHPR + floor(VIT / 5)`. */
  fun hpRegen(maxHp: Int, vitality: Int): Int =
    max(1, maxHp / 200) + vitality / 5

  /**
   * `BaseMPR = 1 + floor(MaxMana / 100) + floor(INT / 6)`, plus the docs' high-intelligence bonus of
   * `4 + floor((INT - 120) / 2)` from 120 INT upwards - a deliberate discontinuity that makes the
   * last stretch of INT worth buying for a caster.
   */
  fun manaRegen(maxMana: Int, intelligence: Int): Int {
    val base = 1 + maxMana / 100 + intelligence / 6

    return if (intelligence >= HIGH_INTELLIGENCE_THRESHOLD) {
      base + 4 + (intelligence - HIGH_INTELLIGENCE_THRESHOLD) / 2
    } else {
      base
    }
  }

  /** `BaseSTAR = max(1, floor(MaxSta / 150))`, `STAR = BaseSTAR + floor(VIT / 6) + floor(WIL / 8)`. */
  fun staminaRegen(maxStamina: Int, vitality: Int, willpower: Int): Int =
    max(1, maxStamina / 150) + vitality / 6 + willpower / 8

  companion object {
    private const val HIGH_INTELLIGENCE_THRESHOLD = 120
  }
}
