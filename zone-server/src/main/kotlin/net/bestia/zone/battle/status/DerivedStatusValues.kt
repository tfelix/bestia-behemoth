package net.bestia.zone.battle.status

/**
 * Secondary combat values derived from an entity's level + effective primary [StatusValues]. Resolved once, with
 * the rest of its [CombatValues], whenever something that feeds them changes - a buff/debuff that changed the
 * primaries therefore flows through here when the recalc runs, and a fight just reads the result.
 *
 * Formulas follow the game docs (https://docs.bestia-game.net/docs/mechanics/statusvalues/),
 * simplified to the parts expressible today: the additive `ModSum` terms of HIT, FLEE and CRIT arrive as [hitBonus],
 * [fleeBonus] and [critBonus] - [CombatValues] collects them from the recalc context - and the multiplicative
 * `ModPerc` terms are omitted (`ModPerc = 1`). Gear otherwise reaches the damage formula through
 * [CombatValues.weapon] and [DefenseValues] and through the primary attributes these are derived from.
 */
data class DerivedStatusValues(
  val atk: Int,

  /**
   * The [atk] a bow or a sling uses, with STR and DEX swapped.
   *
   * The docs give one ATK, and it is the melee one. A ranged weapon that scaled off STR would make an archer
   * a swordsman who stands further away, so the two roles need two numbers - and swapping exactly those two
   * attributes is what Ragnarok Online's pre-renewal BaseATK does for the same reason. Everything else about
   * the term is identical, deliberately: an archer is not meant to hit harder than a swordsman, only to hit
   * off a different attribute.
   */
  val rangedAtk: Int,

  val matk: Int,
  val hitrate: Int,
  val flee: Int,
  val crit: Int
) {

  companion object {
    fun fromStatusValues(
      lv: Int,
      sv: StatusValues,
      hitBonus: Int = 0,
      fleeBonus: Int = 0,
      critBonus: Int = 0
    ): DerivedStatusValues {
      // The squared terms are RO pre-renewal's: they let attack outgrow the flat soft defence at high levels.
      // ATK (melee) = BaseLevel/4 + STR + (STR/10)² + DEX/5 + WIL/3
      val atk = lv / 4 + sv.strength + square(sv.strength / 10) + sv.dexterity / 5 + sv.willpower / 3
      // ATK (ranged) = the same with STR and DEX swapped
      val rangedAtk = lv / 4 + sv.dexterity + square(sv.dexterity / 10) + sv.strength / 5 + sv.willpower / 3
      // MATK = BaseLevel/4 + INT + (INT/7)² + WIL/5
      val matk = lv / 4 + sv.intelligence + square(sv.intelligence / 7) + sv.willpower / 5
      // HIT = 175 + BaseLv + DEX + WIL/3 + HitModSum
      val hitrate = 175 + lv + sv.dexterity + sv.willpower / 3 + hitBonus
      // FLEE = 100 + BaseLv + AGI + WIL/5 + FleeModSum
      val flee = 100 + lv + sv.agility + sv.willpower / 5 + fleeBonus
      // CRIT = WIL/3 + Bonus
      val crit = sv.willpower / 3 + critBonus

      return DerivedStatusValues(
        atk = atk,
        rangedAtk = rangedAtk,
        matk = matk,
        hitrate = hitrate,
        flee = flee,
        crit = crit
      )
    }

    private fun square(value: Int): Int {
      return value * value
    }
  }
}
