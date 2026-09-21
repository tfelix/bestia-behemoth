package net.bestia.zone.ai.core.state

/**
 * An appetite that rises on its own until something spends it - hunger, tiredness, boredom.
 *
 * Every drive states which clock its rate is against, because the two kinds are genuinely different
 * questions. Hunger and tiredness belong to the world's day, so they are authored per in-game hour and
 * survive a retune of `speed-factor`. Boredom is about how long a creature has stood still where somebody
 * can watch it, so it is authored per real second and no world clock may touch it.
 *
 * A day here lasts eight real hours, so a mob tuned to get peckish in three real minutes and a townsperson
 * who eats at noon are two orders of magnitude apart; saying the unit is what stops one domain's tuning
 * reading as a mistake in the other's.
 */
class Drive private constructor(
  val key: StateKey<Int>,
  val pace: Pace,
  val rate: Float,
  val whileSleepingRate: Float,
) {

  /** Which clock a [rate] is measured against. */
  enum class Pace {
    GAME_HOUR,
    REAL_SECOND,
  }

  /**
   * Where the sub-integer remainder lives, so a rate slower than one point per sweep still accumulates.
   *
   * Derived from the drive's own name rather than looked up, which is what lets a domain add a drive
   * without also having to register it somewhere else.
   */
  val fractionKey: StateKey<Float> = StateKey("${key.name}Fraction", retain = Blackboard.PERMANENT)

  /** How far this drive moves over a sweep covering [gameHours] of world time and [realSeconds] of wall time. */
  fun amountOver(gameHours: Float, realSeconds: Float, asleep: Boolean): Float {
    val effective = if (asleep) whileSleepingRate else rate

    return when (pace) {
      Pace.GAME_HOUR -> effective * gameHours
      Pace.REAL_SECOND -> effective * realSeconds
    }
  }

  companion object {
    /** [whileSleeping] defaults to [rate]: lying down does not feed you, and it does not make you less bored. */
    fun perGameHour(key: StateKey<Int>, rate: Float, whileSleeping: Float = rate): Drive {
      return Drive(key, Pace.GAME_HOUR, rate, whileSleeping)
    }

    fun perRealSecond(key: StateKey<Int>, rate: Float, whileSleeping: Float = rate): Drive {
      return Drive(key, Pace.REAL_SECOND, rate, whileSleeping)
    }
  }
}
