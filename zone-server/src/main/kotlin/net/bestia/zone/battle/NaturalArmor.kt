package net.bestia.zone.battle

/**
 * The hard DEF and MDEF a creature's own body gives it: scales, a shell, a body of stone or of spirit. A master's
 * armour is what it wears; a bestia's grows with it, by kind and level, so a level 90 dragon shrugs off a share of a
 * sword the way a level 90 knight in plate does.
 *
 * Percent per ten levels, from the bestia design docs. A boss is half again as hard, and nothing passes [CAP].
 */
object NaturalArmor {

  data class Armor(val defense: Int, val magicDefense: Int) {
    companion object {
      val NONE = Armor(0, 0)
    }
  }

  /** Hard DEF and MDEF, percent per ten levels. */
  private val perTenLevels: Map<BestiaKind, Armor> = mapOf(
    BestiaKind.BEAST to Armor(1, 0),
    BestiaKind.BIRD to Armor(1, 1),
    BestiaKind.REPTILE to Armor(2, 0),
    BestiaKind.AQUATIC to Armor(1, 1),
    BestiaKind.INSECT to Armor(2, 0),
    BestiaKind.PLANT to Armor(1, 1),
    BestiaKind.FORMLESS to Armor(0, 2),
    BestiaKind.ELEMENTAL to Armor(1, 3),
    BestiaKind.HUMANOID to Armor(1, 1),
    BestiaKind.UNDEAD to Armor(2, 1),
    BestiaKind.DEMON to Armor(2, 2),
    BestiaKind.DRAGON to Armor(3, 2),
  )

  /** No kind (a master, a prop) has no natural armour. */
  fun of(kind: BestiaKind?, level: Int, boss: Boolean): Armor {
    val rate = kind?.let { perTenLevels[it] } ?: return Armor.NONE
    val bossPercent = if (boss) BOSS_PERCENT else 100

    return Armor(
      defense = scaled(rate.defense, level, bossPercent),
      magicDefense = scaled(rate.magicDefense, level, bossPercent),
    )
  }

  private fun scaled(perTen: Int, level: Int, bossPercent: Int): Int {
    return (perTen * level.coerceAtLeast(0) * bossPercent / 1000).coerceAtMost(CAP)
  }

  private const val BOSS_PERCENT = 150
  const val CAP = 60
}
