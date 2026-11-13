package net.bestia.zone.spellbinding

/** The skill's level table on the Scholar tree page: bindings at once and the strongest scroll a level may bind. */
object SpellBindingRules {

  const val MAX_SKILL_LEVEL = 5

  /** How close "comes near" reaches, and how far an "after a delay" binding looks for a target: 5 m. */
  const val NEAR_RADIUS_TILES = 5L

  /** "Holder's HP gets low" fires when a hit takes the holder below this share of max HP. */
  const val LOW_HEALTH_PERCENT = 30

  const val MIN_DELAY_SECONDS = 5L
  const val MAX_DELAY_SECONDS = 300L

  private const val ATTACK_LEVELS_PER_SKILL_LEVEL = 20

  fun maxBindings(skillLevel: Int): Int {
    return skillLevel.coerceIn(1, MAX_SKILL_LEVEL)
  }

  /** The attack level is the bestia level that learns the attack, as for Spell Inscription. Level 5 binds any. */
  fun maxAttackLevel(skillLevel: Int): Int {
    if (skillLevel >= MAX_SKILL_LEVEL) {
      return Int.MAX_VALUE
    }

    return ATTACK_LEVELS_PER_SKILL_LEVEL * skillLevel.coerceAtLeast(1)
  }
}
