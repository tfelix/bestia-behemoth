package net.bestia.zone.capture

import org.springframework.stereotype.Component

/**
 * `P_catch = clamp(0%, 95%, base_trap + Σ bonus)`, from `bestia-docs/content/docs/mechanics/bestias/bestia.md`.
 * A boss multiplies that by [BOSS_FACTOR].
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
    val trappingLevel: Int,
    val beastfriendLevel: Int,
    /** From a Tamer essence the trapper wears. */
    val essencePoints: Int = 0,
    /** A boss or world boss. */
    val boss: Boolean = false,
  )

  /** @return the chance in `0.0..0.95`, or `0.0..0.0285` against a boss. */
  fun chance(input: Input): Double {
    val points = input.tier.baseChance(input.targetLevel) +
        hpBonus(input.targetHp, input.targetMaxHp) +
        input.trapperWillpower / WILLPOWER_PER_POINT +
        TRAPPING_PER_LEVEL * input.trappingLevel +
        BEASTFRIEND_PER_LEVEL * input.beastfriendLevel +
        input.essencePoints

    val chance = (points / 100.0).coerceIn(0.0, MAX_CHANCE)

    return if (input.boss) chance * BOSS_FACTOR else chance
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
    const val TRAPPING_PER_LEVEL = 6
    const val BEASTFRIEND_PER_LEVEL = 5

    /** Even a perfect setup can fail. */
    const val MAX_CHANCE = 0.95

    /**
     * A boss essence is the rarest essence there is. The factor keeps every bonus worth having, and the best
     * possible setup still ends near 3%.
     */
    const val BOSS_FACTOR = 0.03
  }
}
