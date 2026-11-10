package net.bestia.zone.casting

import net.bestia.zone.skill.persistence.Skill

/**
 * What one level of a skill costs to cast and how far it reaches.
 *
 * `skills.yml` holds one value of each, which is all a bestia skill needs. A master skill often grows per level -
 * First Aid costs 12, 16 and 20 mana, Charge cools down faster each rank - and that table belongs to the skill's
 * script, next to the rest of what the level buys. [SkillStrategy.costOf] is where a script says so.
 */
data class SkillLevelCost(
  val manaCost: Int,
  val staminaCost: Int,
  val cooldownSeconds: Float,
  val castTimeSeconds: Float,
  val rangeTiles: Long,
) {

  companion object {
    /** The catalogue's single values, for a skill whose cost does not change with its level. */
    fun of(skill: Skill): SkillLevelCost {
      return SkillLevelCost(
        manaCost = skill.manaCost,
        staminaCost = 0,
        cooldownSeconds = skill.cooldown,
        castTimeSeconds = skill.castTime,
        rangeTiles = skill.range?.toLong() ?: DEFAULT_RANGE_TILES,
      )
    }

    /** A skill whose row names no range reaches the next tile, like a bare swing. */
    private const val DEFAULT_RANGE_TILES = 1L
  }
}
