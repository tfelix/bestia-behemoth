package net.bestia.zone.casting

/**
 * An attack on a spell scroll, or one a bestia could copy onto one, at [level]. [attackLevel] is the level a bestia
 * learns it at, which is what the odds of Spell Training and Spell Inscription go by.
 */
data class InscribedSpell(
  val skillId: Long,
  val level: Int,
  val attackLevel: Int,
)
