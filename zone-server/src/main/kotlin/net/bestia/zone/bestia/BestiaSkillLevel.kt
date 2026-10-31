package net.bestia.zone.bestia

/**
 * A bestia does not level its skills up; it knows each one at the level it was learned with
 * (`bestia-design.md`, learnset rule): one more for every ten levels of the learnset slot, up to ten.
 */
object BestiaSkillLevel {

  private const val LEVELS_PER_SKILL_LEVEL = 10
  private const val MAX_SKILL_LEVEL = 10

  fun forLearnLevel(learnLevel: Int): Int {
    return (1 + learnLevel / LEVELS_PER_SKILL_LEVEL).coerceAtMost(MAX_SKILL_LEVEL)
  }
}
