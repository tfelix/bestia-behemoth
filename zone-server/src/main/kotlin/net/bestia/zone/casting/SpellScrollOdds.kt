package net.bestia.zone.casting

/** The odds of Spell Training and Spell Inscription, in percent, as the Sage's docs give them. */
object SpellScrollOdds {

  private const val PERCENT_PER_SKILL_LEVEL = 10
  private const val MAX_SKILL_LEVEL = 5

  /** A failed extraction breaks the bestia's mind this often. */
  const val MIND_BREAK_PERCENT = 50

  /** What either skill starts from, by the level a bestia learns the attack at. */
  fun basePercent(attackLevel: Int): Int {
    return when {
      attackLevel <= 20 -> 80
      attackLevel <= 40 -> 70
      attackLevel <= 60 -> 60
      attackLevel <= 80 -> 50
      attackLevel <= 90 -> 10
      attackLevel <= 100 -> -20
      else -> -40
    }
  }

  fun trainingPercent(attackLevel: Int, skillLevel: Int): Int {
    return (basePercent(attackLevel) + skillBonus(skillLevel)).coerceIn(0, 100)
  }

  fun inscriptionPercent(attackLevel: Int, skillLevel: Int, intelligence: Int, willpower: Int): Int {
    val mind = intelligence / 2 + willpower / 4

    return (basePercent(attackLevel) + skillBonus(skillLevel) + mind).coerceIn(0, 100)
  }

  private fun skillBonus(skillLevel: Int): Int {
    return PERCENT_PER_SKILL_LEVEL * skillLevel.coerceIn(1, MAX_SKILL_LEVEL)
  }

  /** The chance a single extraction loses the bestia: it has to fail, and then break its mind. */
  fun mindBreakPercent(successPercent: Int): Int {
    return (100 - successPercent) * MIND_BREAK_PERCENT / 100
  }
}
