package net.bestia.zone.battle.status

/**
 * What an entity subtracts from an incoming hit, in Ragnarok Online's two kinds.
 *
 * [defense] / [magicDefense] are the **soft** kind: derived from level + effective primary [StatusValues]
 * per the game docs (https://docs.bestia-game.net/docs/mechanics/statusvalues/), and subtracted flat, last,
 * after every multiplier. [hardDefense] / [hardMagicDefense] are the **hard** kind, carried by worn
 * equipment: percentage points of damage removed, applied as a multiplier, and bypassed entirely by a
 * critical hit. `net.bestia.zone.battle.damage.BaseDamageCalculator` is where both are spent.
 *
 * The hard pair defaults to zero, which is the honest answer for everything that wears nothing - every mob,
 * every promoted prop - and keeps this the one place either formula lives.
 */
data class DefenseValues(
  val magicDefense: Int,
  val defense: Int,
  val hardDefense: Int = 0,
  val hardMagicDefense: Int = 0
) {

  companion object {
    fun fromStatusValues(
      lv: Int,
      sv: StatusValues,
      hardDefense: Int = 0,
      hardMagicDefense: Int = 0
    ): DefenseValues {
      // SoftDEF  = VIT + STR/5 + AGI/5 + BaseLv/4
      val defense = sv.vitality + sv.strength / 5 + sv.agility / 5 + lv / 4
      // SoftMDEF = INT + VIT/5 + DEX/5 + BaseLv/4
      val magicDefense = sv.intelligence + sv.vitality / 5 + sv.dexterity / 5 + lv / 4

      return DefenseValues(
        magicDefense = magicDefense,
        defense = defense,
        hardDefense = hardDefense,
        hardMagicDefense = hardMagicDefense
      )
    }
  }
}
