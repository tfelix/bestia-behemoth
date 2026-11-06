package net.bestia.zone.item.enchant

/** One entry of `item_enchantments.yml`. Config, so it is never persisted. */
data class ItemEnchantmentDefinition(
  val id: Long,
  val identifier: String,
  val script: String,
  /** False for an enchantment no Dispel may break. */
  val dispellable: Boolean = true,
)
