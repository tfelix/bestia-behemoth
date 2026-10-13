package net.bestia.zone.battle

data class Weapon(
  val atk: Int,
  val matk: Int,
  val upgradeLevel: Int
) {

  /**
   * What the weapon adds to a physical hit, refinement included - the one place refinement is priced, read by the
   * damage formula and the status window alike. [atk] is the un-refined attack a worn item contributed.
   *
   * Linear rather than RO's quadratic over-refine curve: without weapon levels there is nothing to scale a per-refine
   * step by.
   */
  val physicalAtk: Int
    get() {
      return atk + upgradeLevel * REFINE_ATTACK_PER_LEVEL
    }

  companion object {
    private const val REFINE_ATTACK_PER_LEVEL = 2

    /** What an entity holding nothing swings: every mob, every prop. */
    val NONE = Weapon(atk = 0, matk = 0, upgradeLevel = 0)
  }
}
