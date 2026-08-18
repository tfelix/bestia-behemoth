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
 * The docs' `HPRMod` / `MPRMod` / `STARMod` arrive through [applyModifier]: equipment, status
 * effects and passive skills accumulate into a [RegenModifier] on [StatusValueRecalcContext], which
 * `StatusValueRecalcSystem` resolves into a
 * [net.bestia.zone.ecs.battle.status.RegenerationModifiers] component for the regen systems to read.
 * The base rates below stay modifier-free; [applyModifier] is the only place the two meet.
 *
 * Still unmodelled: the docs' "doubles while resting", because there is no resting state - the
 * nearest thing today is the `InCombat` gate the calling systems already apply. Left out entirely
 * rather than taken as a parameter no caller passes, so nothing here is shipped dead.
 *
 * Integer division throughout, deliberately: for non-negative operands `/` **is** the docs' `floor`.
 * Doing this in [Double] and rounding at the end is what produced the operator-precedence bug this
 * class replaced (`max * vit / 99.0 + 2.0 / 100.0`, which parses as `(max * vit / 99) + 0.02` and is
 * ~19x the documented rate at level 100).
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

  /**
   * Resolves a base rate against everything modifying it, as the docs' `(base + ModSum) * ModPerc`.
   * A null [modifier] is the common case - nothing worn, buffed or learned affects this pool - and
   * returns [baseRegen] untouched.
   *
   * Flat first, then the percentage, so a percentage bonus scales the geared value rather than the
   * naked one. That is the same ordering rationale `StatusValueRecalcSystem` already documents for
   * applying equipment before status effects.
   *
   * ### Two deliberate properties
   *
   * **Clamped at zero, never negative.** A suppression debuff must be able to stop regeneration
   * dead, but must never invert it into damage - a regen system only ever `+=`s what this returns.
   * The clamp is load-bearing rather than defensive: below `-100%` the product itself goes negative,
   * and Kotlin's integer division truncates *toward zero* rather than flooring once it does.
   *
   * **A small percentage of a small base rounds away.** At the level-1 base rate of 2 HP per tick,
   * every bonus below `+50%` truncates straight back to 2. That follows the docs, which floor this
   * result, and it makes a percentage passive an inherently late-game one while the placeholder base
   * values keep low-level pools tiny. Pinned by a test so it stays a known property.
   */
  fun applyModifier(baseRegen: Int, modifier: RegenModifier?): Int {
    if (modifier == null) {
      return baseRegen
    }

    return max(0, (baseRegen + modifier.flat) * (100 + modifier.percent) / 100)
  }

  companion object {
    private const val HIGH_INTELLIGENCE_THRESHOLD = 120
  }
}
