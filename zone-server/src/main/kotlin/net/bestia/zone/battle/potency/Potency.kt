package net.bestia.zone.battle.potency

import kotlin.math.min

/**
 * How strong a cast is, and how two casts decide a contest: the design docs' "Spell Potency and Rituals".
 *
 * ```
 * P = Rank + Level + Mind + Focus + Place
 * Edge = 0.5 + (A - D) / 50, clamped to 0..1
 * ```
 *
 * Potency is fixed at the cast and travels with whatever the skill leaves behind. It never changes the numbers in a
 * skill's own table; only a contest reads it.
 */
object Potency {

  const val MAX_RANK = 30
  const val MAX_LEVEL_TERM = 30
  const val MAX_MIND = 30
  const val MAX_FOCUS = 10

  /** No circle binds more than this, however strong. */
  const val RITUAL_CAP = 150

  /** What one Bestia attack level is worth in the rank term: a level 7 attack gives 21. */
  const val BESTIA_RANK_PER_LEVEL = 3

  fun of(rank: Int, maxRank: Int, level: Int, intelligence: Int, willpower: Int, focus: Int, place: Int): Int {
    return rankTerm(rank, maxRank) + levelTerm(level) + mind(intelligence, willpower) +
        focus.coerceIn(0, MAX_FOCUS) + place
  }

  fun rankTerm(rank: Int, maxRank: Int): Int {
    if (maxRank <= 0) return 0

    return (MAX_RANK * rank.coerceIn(0, maxRank) / maxRank)
  }

  fun levelTerm(level: Int): Int = (level / 4).coerceIn(0, MAX_LEVEL_TERM)

  /** `30 x M / (M + 100)` with `M = INT + WIL / 2`, the flattening curve status resistance uses too. */
  fun mind(intelligence: Int, willpower: Int): Int {
    val m = (intelligence + willpower / 2).coerceAtLeast(0)

    return MAX_MIND * m / (m + 100)
  }

  /** The chance or share an attacker of potency [attacker] gets against [defender]: a coin flip when equal. */
  fun edge(attacker: Int, defender: Int): Double = (0.5 + (attacker - defender) / 50.0).coerceIn(0.0, 1.0)

  /**
   * Dispel keeps the base chance of its rank and loses only to a stronger target: `base x min(1, Pd / Pt)`.
   * A target guarded by a [threshold] above the dispel's potency is out of reach altogether.
   */
  fun dispelChancePercent(baseChancePercent: Int, dispel: Int, target: Int, threshold: Int = 0): Double {
    if (dispel < threshold) return 0.0
    if (target <= 0) return baseChancePercent.toDouble()

    return baseChancePercent * min(1.0, dispel.toDouble() / target)
  }

  /** What a creature defends a hostile spell with. `30` stands for its own mind, known like a fully learned skill. */
  fun resolve(level: Int, intelligence: Int, willpower: Int, focus: Int): Int {
    return MAX_RANK + levelTerm(level) + mind(intelligence, willpower) + focus.coerceIn(0, MAX_FOCUS)
  }

  /** A ritual charge's surge on damage, healing, shields and buff length: `+1%` per 4 points, at most `+25%`. */
  fun surgePercent(ritualBonus: Int): Int = (ritualBonus / 4).coerceIn(0, 25)

  fun band(potency: Int): Band = Band.entries.last { potency >= it.from }

  enum class Band(val from: Int) {
    FAINT(Int.MIN_VALUE),
    STEADY(30),
    STRONG(60),
    MIGHTY(90),
    OVERWHELMING(120),
  }
}
