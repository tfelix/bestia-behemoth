package net.bestia.zone.item.profession

import net.bestia.zone.battle.gear.ArmorType
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.gear.WeaponType.*

/**
 * A master's profession: a sub-tree they spent points in. It decides which weapon types and how heavy an armor
 * the master may use. The two sub-trees with the most points are the master's professions, see
 * [ProfessionResolver]. The table is the design docs' "Equipment per Profession".
 */
enum class Profession(
  /** The `subTree` of `master_skill_tree.yml` whose points make a master this. */
  val subTree: String,
  val heaviestArmor: ArmorType,
  val weapons: Set<WeaponType>,
) {
  BLACKSMITH("BLACKSMITH", ArmorType.MEDIUM, setOf(MACE, HAMMER, AXE, GREATAXE)),
  ALCHEMIST("ALCHEMIST", ArmorType.LIGHT, setOf(DAGGER, MACE, THROWN)),
  ARTIFICER("ARTIFICER", ArmorType.MEDIUM, setOf(DAGGER, WAND, STAFF, CROSSBOW)),
  FORESTER("FORESTER", ArmorType.MEDIUM, setOf(AXE, GREATAXE, SPEAR, BOW)),
  PROSPECTOR("PROSPECTOR", ArmorType.MEDIUM, setOf(SWORD, SPEAR, CROSSBOW)),
  MINER("MINER", ArmorType.MEDIUM, setOf(MACE, HAMMER, GREATAXE)),
  TRADER("TRADER", ArmorType.LIGHT, setOf(SWORD, CROSSBOW)),
  SAGE("SAGE", ArmorType.CLOTH, setOf(WAND, STAFF, TOME)),
  PRIEST("PRIEST", ArmorType.MEDIUM, setOf(MACE, STAFF, TOME)),
  WIZARD("WIZARD", ArmorType.CLOTH, setOf(WAND, STAFF, TOME)),
  ASSASSIN("ASSASSIN", ArmorType.LIGHT, setOf(DAGGER, FIST, SWORD, THROWN)),
  KNIGHT("KNIGHT", ArmorType.HEAVY, setOf(SWORD, GREATSWORD, AXE, GREATAXE, MACE, HAMMER, SPEAR, POLEARM)),
  BRAWLER("BRAWLER", ArmorType.LIGHT, setOf(FIST, STAFF)),
  HUNTER("HUNTER", ArmorType.LIGHT, setOf(SPEAR, BOW, CROSSBOW, THROWN)),
  BARD("BARD", ArmorType.LIGHT, setOf(WHIP, BOW, INSTRUMENT));

  companion object {
    fun ofSubTree(subTree: String): Profession? = entries.firstOrNull { it.subTree == subTree }

    /** Reads the comma-separated names a master row stores, skipping any the enum no longer has. */
    fun listOf(stored: String): List<Profession> {
      return stored.split(',').mapNotNull { name -> entries.firstOrNull { it.name == name.trim() } }
    }

    fun stored(professions: List<Profession>): String = professions.joinToString(",") { it.name }
  }
}
