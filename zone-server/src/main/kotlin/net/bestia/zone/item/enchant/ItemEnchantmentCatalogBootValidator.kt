package net.bestia.zone.item.enchant

import net.bestia.zone.BestiaException
import net.bestia.zone.util.CatalogValidator
import org.springframework.stereotype.Component

/** Cross-checks [ItemEnchantmentId] against `item_enchantments.yml`, and that every script is a bean. */
@Component
class ItemEnchantmentCatalogBootValidator(
  private val catalog: ItemEnchantmentCatalog,
  private val scripts: ItemEnchantmentScripts,
) : CatalogValidator {

  override fun validate() {
    val problems = mutableListOf<String>()

    ItemEnchantmentId.entries.forEach { enchantment ->
      val definition = catalog.findById(enchantment.id)
      when {
        definition == null ->
          problems += "ItemEnchantmentId.${enchantment.name} (id=${enchantment.id}) has no entry in item_enchantments.yml"

        definition.identifier != enchantment.name ->
          problems += "ItemEnchantmentId.${enchantment.name} (id=${enchantment.id}) does not match " +
            "item_enchantments.yml identifier '${definition.identifier}'"
      }
    }

    catalog.all()
      .filter { ItemEnchantmentId.findById(it.id) == null }
      .forEach { problems += "item_enchantments.yml '${it.identifier}' (id=${it.id}) has no ItemEnchantmentId constant" }

    catalog.all()
      .filter { scripts.get(it.script) == null }
      .forEach { problems += "item_enchantments.yml '${it.identifier}' names script '${it.script}', which no bean implements" }

    if (problems.isNotEmpty()) {
      throw ItemEnchantmentCatalogMismatchException(problems)
    }
  }
}

class ItemEnchantmentCatalogMismatchException(problems: List<String>) : BestiaException(
  code = "ITEM_ENCHANTMENT_CATALOG_MISMATCH",
  message = "ItemEnchantmentId and item_enchantments.yml are out of sync:\n" +
    problems.joinToString("\n") { "  - $it" }
)
