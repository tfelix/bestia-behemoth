package net.bestia.zone.item.enchant

import net.bestia.zone.boot.ItemEnchantmentImporterBootRunner

/** The real `item_enchantments.yml` with only [scripts] behind it; none for a test that wields nothing enchanted. */
fun itemEnchantmentScriptsOf(vararg scripts: ItemEnchantmentScript): ItemEnchantmentScripts {
  val catalog = ItemEnchantmentCatalog().apply { load(ItemEnchantmentImporterBootRunner.load()) }

  return ItemEnchantmentScripts(scripts.toList(), catalog)
}
