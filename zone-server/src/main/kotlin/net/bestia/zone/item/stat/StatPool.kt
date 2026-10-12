package net.bestia.zone.item.stat

/** A group of stats a template slot may roll from, so `items.yml` does not list them one by one. */
enum class StatPool(val stats: List<ItemStat>) {
  ATTRIBUTE(listOf(ItemStat.STR, ItemStat.AGI, ItemStat.VIT, ItemStat.INT, ItemStat.DEX, ItemStat.WIL)),
  OFFENSE(listOf(ItemStat.ATK, ItemStat.MATK, ItemStat.HIT, ItemStat.CRIT)),
  DEFENSE(listOf(ItemStat.DEF, ItemStat.MDEF, ItemStat.FLEE, ItemStat.MAX_HP)),
  ARCANE(
    listOf(ItemStat.MAX_MANA, ItemStat.CAST_TIME, ItemStat.HP_REGEN, ItemStat.MANA_REGEN, ItemStat.STAMINA_REGEN)
  ),
  RESIST(ItemStat.entries.filter { it.name.startsWith("RESIST_") })
}
