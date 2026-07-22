package net.bestia.zone.battle.status

/**
 * Soft defense values derived from level + effective primary [StatusValues], per the game docs
 * (https://docs.bestia-game.net/docs/mechanics/statusvalues/). "Soft" defense scales with
 * attributes; hard (equipment) defense is a separate, not-yet-modelled term.
 */
data class DefenseValues(
  val magicDefense: Int,
  val defense: Int
) {

  companion object {
    fun fromStatusValues(
      lv: Int,
      sv: StatusValues
    ): DefenseValues {
      // SoftDEF  = VIT + STR/5 + AGI/5 + BaseLv/4
      val defense = sv.vitality + sv.strength / 5 + sv.agility / 5 + lv / 4
      // SoftMDEF = INT + VIT/5 + DEX/5 + BaseLv/4
      val magicDefense = sv.intelligence + sv.vitality / 5 + sv.dexterity / 5 + lv / 4

      return DefenseValues(
        magicDefense = magicDefense,
        defense = defense
      )
    }
  }
}
