package net.bestia.zone.capture

import org.springframework.stereotype.Component

/**
 * `P_catch = clamp(0%, 95%, base_trap + Σ bonus)`, from `bestia-docs/content/docs/mechanics/bestia.md`.
 *
 * The bonuses are additive percentage points, as the docs specify, so a good setup can make up for a weak
 * trap but not for an ancient target.
 */
@Component
class CaptureChanceCalculator {

  class Input(
    val tier: TrapTier,
    val targetLevel: Int,
    val targetHp: Int,
    val targetMaxHp: Int,
    val trapperWillpower: Int,
    val bestiaTrappingLevel: Int,
    val beastfriendLevel: Int,
  )

  /** @return the chance in `0.0..0.95`. */
  fun chance(input: Input): Double {
    val points = input.tier.baseChance(input.targetLevel) +
        hpBonus(input.targetHp, input.targetMaxHp) +
        input.trapperWillpower / WILLPOWER_PER_POINT +
        BESTIA_TRAPPING_PER_LEVEL * input.bestiaTrappingLevel +
        BEASTFRIEND_PER_LEVEL * input.beastfriendLevel

    return (points / 100.0).coerceIn(0.0, MAX_CHANCE)
  }

  private fun hpBonus(hp: Int, maxHp: Int): Double {
    if (maxHp <= 0) {
      return 0.0
    }
    val missing = 1.0 - hp.coerceIn(0, maxHp).toDouble() / maxHp

    return MAX_HP_BONUS * missing
  }

  private companion object {
    const val MAX_HP_BONUS = 30.0
    const val WILLPOWER_PER_POINT = 50.0
    const val BESTIA_TRAPPING_PER_LEVEL = 6
    const val BEASTFRIEND_PER_LEVEL = 5

    /** Even a perfect setup can fail. */
    const val MAX_CHANCE = 0.95
  }
}
