package net.bestia.zone.battle.status

/**
 * What an entity subtracts from an incoming hit, in Ragnarok Online's two kinds.
 *
 * [defense] / [magicDefense] are the **soft** kind: derived from level + effective primary [StatusValues]
 * per the game docs (https://docs.bestia-game.net/docs/mechanics/combat/statusvalues/), and subtracted flat, last,
 * after every multiplier. Physical soft defence is RO pre-renewal's player formula: a fixed [defense] plus a
 * roll below [defenseSpread], made per hit by the damage calculator. It stays a small share of a same-level
 * hit, where the old `VIT + STR/5 + AGI/5 + Lv/4` swallowed half of it. [hardDefense] / [hardMagicDefense] are the **hard** kind, carried by worn
 * equipment: percentage points of damage removed, applied as a multiplier, and bypassed entirely by a
 * critical hit. `net.bestia.zone.battle.damage.BaseDamageCalculator` is where both are spent.
 *
 * The hard pair defaults to zero, which is the honest answer for everything that wears nothing - every mob,
 * every promoted prop - and keeps this the one place either formula lives.
 */
data class DefenseValues(
  val magicDefense: Int,
  val defense: Int,
  val defenseSpread: Int = 0,
  val hardDefense: Int = 0,
  val hardMagicDefense: Int = 0
) {

  /**
   * Both kinds of hard defence, physical and magic, moved by [points] of damage reduction, never below none.
   * Added rather than scaled, so a freeze takes a body that stopped 80% of a hit to 90%.
   */
  fun withHardDefenseRaisedBy(points: Int): DefenseValues {
    return copy(
      hardDefense = (hardDefense + points).coerceAtLeast(0),
      hardMagicDefense = (hardMagicDefense + points).coerceAtLeast(0)
    )
  }

  /** Physical defence, soft and hard, cut by [percent], as a sigil that pries the guard open. Magic defence stays. */
  fun withPhysicalDefenseCutBy(percent: Int): DefenseValues {
    val kept = (100 - percent).coerceIn(0, 100)

    return copy(defense = defense * kept / 100, defenseSpread = defenseSpread * kept / 100, hardDefense = hardDefense * kept / 100)
  }

  /** The soft magic defence cut by [percent], as Mindbreak wears it down. The hard kind stays. */
  fun withSoftMagicDefenseCutBy(percent: Int): DefenseValues {
    return copy(magicDefense = magicDefense * (100 - percent).coerceIn(0, 100) / 100)
  }

  companion object {
    fun fromStatusValues(
      lv: Int,
      sv: StatusValues,
      hardDefense: Int = 0,
      hardMagicDefense: Int = 0
    ): DefenseValues {
      // SoftDEF  = VIT/2 + rnd(0 until VIT * (VIT - 15) / 150)
      val defense = sv.vitality / 2
      val defenseSpread = (sv.vitality * (sv.vitality - 15) / 150).coerceAtLeast(0)
      // SoftMDEF = INT + VIT/5 + DEX/5 + BaseLv/4
      val magicDefense = sv.intelligence + sv.vitality / 5 + sv.dexterity / 5 + lv / 4

      return DefenseValues(
        magicDefense = magicDefense,
        defense = defense,
        defenseSpread = defenseSpread,
        hardDefense = hardDefense,
        hardMagicDefense = hardMagicDefense
      )
    }
  }
}
