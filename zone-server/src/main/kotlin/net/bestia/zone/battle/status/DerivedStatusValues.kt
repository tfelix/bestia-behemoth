package net.bestia.zone.battle.status

/**
 * Secondary combat values derived on demand from a character's level + effective primary
 * [StatusValues]. Because they are recomputed from the (already recalc-fresh) attributes each time
 * a fight is set up, a buff/debuff that changed the primaries automatically flows through here
 * without any stored/synced component of its own.
 *
 * Formulas follow the game docs (https://docs.bestia-game.net/docs/mechanics/statusvalues/),
 * simplified to the parts expressible today: no equipment/skill modifiers, so the docs' additive
 * `Mod`/multiplicative `ModPerc` terms are omitted (`ModSum = 0`, `ModPerc = 1`).
 */
data class DerivedStatusValues(
  val atk: Int,
  val matk: Int,
  val hitrate: Int,
  val flee: Int,
  val crit: Int
) {

  companion object {
    fun fromStatusValues(
      lv: Int,
      sv: StatusValues
    ): DerivedStatusValues {
      // ATK (melee) = BaseLevel/4 + STR + DEX/5 + WIL/3
      val atk = lv / 4 + sv.strength + sv.dexterity / 5 + sv.willpower / 3
      // MATK = BaseLevel/4 + INT + WIL/5
      val matk = lv / 4 + sv.intelligence + sv.willpower / 5
      // HIT = 175 + BaseLv + DEX + WIL/3
      val hitrate = 175 + lv + sv.dexterity + sv.willpower / 3
      // FLEE = 100 + BaseLv + AGI + WIL/5
      val flee = 100 + lv + sv.agility + sv.willpower / 5
      // CRIT = WIL/3
      val crit = sv.willpower / 3

      return DerivedStatusValues(
        atk = atk,
        matk = matk,
        hitrate = hitrate,
        flee = flee,
        crit = crit
      )
    }
  }
}
