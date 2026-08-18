package net.bestia.zone.battle.skill

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
}
