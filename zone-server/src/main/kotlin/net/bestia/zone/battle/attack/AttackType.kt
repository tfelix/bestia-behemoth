package net.bestia.zone.battle.attack

enum class AttackType {
  /**
   * Attack is based on special attack stat its nature is a magic one.
   */
  MAGIC,

  /**
   * Attack is based on normal attack stat since its a physical attack.
   */
  MELEE_PHYSICAL,

  /**
   * Attack is a physical ranged attack.
   */
  RANGED_PHYSICAL,

  /**
   * Attacks which do a special calculation and deal no real damage (it uses
   * none of the battle based stats). All effects or damage is done via
   * scripts which are deployed upon usage of this attack.
   */
  NO_DAMAGE
}
