package net.bestia.zone.item.enchant

import org.springframework.stereotype.Component

/** Every [ItemEnchantmentScript] bean, by its simple class name, like the status effect scripts. */
@Component
class ItemEnchantmentScripts(
  scripts: List<ItemEnchantmentScript>,
  private val catalog: ItemEnchantmentCatalog,
) {

  private val byName: Map<String, ItemEnchantmentScript> = scripts
    .mapNotNull { script -> script::class.simpleName?.let { it to script } }
    .toMap()

  fun get(scriptName: String): ItemEnchantmentScript? {
    return byName[scriptName]
  }

  fun of(enchantment: ItemEnchantmentId): ItemEnchantmentScript {
    val scriptName = catalog.getOrThrow(enchantment).script

    return get(scriptName) ?: throw IllegalStateException("No ItemEnchantmentScript bean named '$scriptName'")
  }
}
