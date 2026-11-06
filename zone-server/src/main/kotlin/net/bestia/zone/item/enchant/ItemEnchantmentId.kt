package net.bestia.zone.item.enchant

/**
 * Every enchantment an item instance can carry, by name. Must stay in lockstep with `item_enchantments.yml`;
 * [ItemEnchantmentCatalogBootValidator] fails the boot if they drift, like [net.bestia.zone.battle.status.StatusEffectId].
 */
enum class ItemEnchantmentId(val id: Long) {
  POSTAL_SEAL(1);

  companion object {
    private val byId = entries.associateBy { it.id }

    fun findById(id: Long): ItemEnchantmentId? {
      return byId[id]
    }
  }
}
