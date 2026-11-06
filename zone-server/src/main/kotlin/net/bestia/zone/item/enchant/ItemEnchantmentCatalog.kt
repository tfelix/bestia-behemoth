package net.bestia.zone.item.enchant

import org.springframework.stereotype.Service

/** The enchantment definitions, filled once at boot by [net.bestia.zone.boot.ItemEnchantmentImporterBootRunner]. */
@Service
class ItemEnchantmentCatalog {

  private var definitionsById: Map<Long, ItemEnchantmentDefinition> = emptyMap()

  fun load(definitions: List<ItemEnchantmentDefinition>) {
    definitionsById = definitions.associateBy { it.id }
  }

  fun findById(id: Long): ItemEnchantmentDefinition? {
    return definitionsById[id]
  }

  fun getOrThrow(enchantment: ItemEnchantmentId): ItemEnchantmentDefinition {
    return findById(enchantment.id)
      ?: throw IllegalStateException("item_enchantments.yml has no entry for ${enchantment.name}")
  }

  fun all(): Collection<ItemEnchantmentDefinition> {
    return definitionsById.values
  }
}
