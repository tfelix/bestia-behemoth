package net.bestia.zone.bestia

/**
 * What a species attacks with when its AI uses no attack skill: a melee swing, a ranged shot, or either one.
 * Reach and speed are the unarmed ones, see `BattleAttack.getBasicMeleeAttack` and `getBasicRangedAttack`.
 */
enum class DefaultAttack(val melee: Boolean, val ranged: Boolean) {
  MELEE(melee = true, ranged = false),
  RANGED(melee = false, ranged = true),
  BOTH(melee = true, ranged = true)
}
