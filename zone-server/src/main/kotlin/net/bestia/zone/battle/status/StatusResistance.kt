package net.bestia.zone.battle.status

/**
 * How much of a status effect an entity shakes off, as a fraction from 0 to [MAX]. It scales the chance to be
 * hit by the effect fully and its duration by half, the way Ragnarok Online weighs VIT against a stun.
 *
 * The attributes grow without end, so the curve flattens instead of reaching immunity: [HALF_POINT] points
 * resist half. Immunity is a species' choice (`status-immunities`), never a stat total.
 */
object StatusResistance {

  const val HALF_POINT = 150.0
  const val PER_LEVEL_LEAD = 0.01
  const val MAX = 0.8

  /**
   * [levelLead] is how many levels the target stands above the one inflicting the effect. [trainedPercent] is what a
   * passive adds against this one status, like Poison Research against poison.
   */
  fun of(statSum: Int, levelLead: Int, trainedPercent: Int = 0): Double {
    val fromStats = if (statSum <= 0) 0.0 else statSum / (statSum + HALF_POINT)

    return (fromStats + levelLead * PER_LEVEL_LEAD + trainedPercent / 100.0).coerceIn(0.0, MAX)
  }

  /**
   * Against a cast: the attributes as above, and the target's Resolve against the cast's Potency in place of the
   * level lead. See [net.bestia.zone.battle.potency.Potency.resolve].
   */
  fun ofPotency(statSum: Int, resolve: Int, potency: Int, trainedPercent: Int = 0): Double {
    val fromStats = if (statSum <= 0) 0.0 else statSum / (statSum + HALF_POINT)

    return (fromStats + (resolve - potency + trainedPercent) / 100.0).coerceIn(0.0, MAX)
  }

  fun chancePercent(baseChancePercent: Double, resistance: Double): Double {
    return baseChancePercent * (1.0 - resistance)
  }

  fun durationSeconds(baseSeconds: Double, resistance: Double): Double {
    return baseSeconds * (1.0 - resistance / 2.0)
  }
}
