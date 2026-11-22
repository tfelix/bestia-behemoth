package net.bestia.zone.battle

import net.bestia.zone.battle.gear.WeaponType

/**
 * How well a weapon fits a body of a [Size]: Ragnarok Online's pre-renewal size table, weapon type against the size
 * of the one being hit. A dagger slips between a rat's ribs and barely scratches a wyrm; a great axe is the other way
 * round. Weighs the weapon's own attack, not the attributes behind it - a strong fighter is strong at any size.
 *
 * Physical hits only. A spell does not care how big its target is.
 *
 * The weapon types RO lacks borrow the row of their nearest kin: wand and staff are RO's rod, a tome its book, a
 * polearm its lance, a hammer its mace, a crossbow, a sling and a thrown weapon its bow, a tool a mace. Bare hands
 * (no weapon type) hit everything alike, which is also every wild mob's natural attack.
 */
object SizeModifier {

  private val NEUTRAL = SizeRow(100, 100, 100)

  private val rows: Map<WeaponType, SizeRow> = mapOf(
    WeaponType.DAGGER to SizeRow(100, 75, 50),
    WeaponType.FIST to SizeRow(100, 100, 75),
    WeaponType.WHIP to SizeRow(75, 100, 50),
    WeaponType.SWORD to SizeRow(75, 100, 75),
    WeaponType.GREATSWORD to SizeRow(75, 75, 100),
    WeaponType.AXE to SizeRow(50, 75, 100),
    WeaponType.GREATAXE to SizeRow(50, 75, 100),
    WeaponType.MACE to SizeRow(75, 100, 100),
    WeaponType.HAMMER to SizeRow(75, 100, 100),
    WeaponType.SPEAR to SizeRow(75, 75, 100),
    WeaponType.POLEARM to SizeRow(75, 75, 100),
    WeaponType.WAND to NEUTRAL,
    WeaponType.STAFF to NEUTRAL,
    WeaponType.TOME to SizeRow(100, 100, 50),
    WeaponType.BOW to SizeRow(100, 100, 75),
    WeaponType.CROSSBOW to SizeRow(100, 100, 75),
    WeaponType.SLING to SizeRow(100, 100, 75),
    WeaponType.THROWN to SizeRow(100, 100, 75),
    WeaponType.INSTRUMENT to SizeRow(75, 100, 75),
    WeaponType.TOOL to SizeRow(75, 100, 100),
  )

  /** The percentage of its attack [weapon] brings against a body of [target] size. 100 for bare hands. */
  fun percentOf(weapon: WeaponType?, target: Size): Int {
    val row = weapon?.let { rows[it] } ?: NEUTRAL

    return when (target) {
      Size.SMALL -> row.small
      Size.MEDIUM -> row.medium
      Size.BIG -> row.big
    }
  }

  fun factorOf(weapon: WeaponType?, target: Size): Float {
    return percentOf(weapon, target) / 100f
  }

  private data class SizeRow(val small: Int, val medium: Int, val big: Int)
}
