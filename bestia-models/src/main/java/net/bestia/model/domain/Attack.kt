package net.bestia.model.domain

interface Attack {

  val id: Int

  val target: AttackTarget
  val databaseName: String
  val strength: Int
  val element: Element
  val manaCost: Int
  val casttime: Int
  val range: Int
  val cooldown: Int
  val indicator: String?
  val type: AttackType
  val hasScript: Boolean
  val needsLineOfSight: Boolean

  val isRanged: Boolean
    get() = type == AttackType.RANGED_MAGIC || type == AttackType.RANGED_PHYSICAL

  /**
   * @return TRUE if the attack is magic or FALSE if its physical.
   */
  val isMagic: Boolean
    get() = type == AttackType.RANGED_MAGIC || type == AttackType.MELEE_MAGIC

  companion object {
    /**
     * Basic attack id used for the default attack every bestia has. Each bestia
     * has the default melee or ranged attack.
     */
    const val DEFAULT_MELEE_ATTACK_ID = -1
    const val DEFAULT_RANGE_ATTACK_ID = -2
  }
}