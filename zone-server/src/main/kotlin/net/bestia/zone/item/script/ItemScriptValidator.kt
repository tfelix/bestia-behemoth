package net.bestia.zone.item.script

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.item.Item
import net.bestia.zone.item.ItemRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Validation class which make sure all consumable have a script available when the server starts.
 * It also validates that no item script is a duplicate.
 */
@Component
@Order(200) // Run after item imports (order 100)
class ItemScriptValidator(
  private val itemRepository: ItemRepository,
  private val itemScripts: List<ItemScript>,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    LOG.info { "Validating ${itemScripts.size} item script(s)..." }

    // Check for duplicate scripts (multiple scripts with the same itemId)
    val scriptsByItemId = itemScripts.groupBy { it.itemId }
    val duplicates = scriptsByItemId.filter { it.value.size > 1 }

    if (duplicates.isNotEmpty()) {
      val duplicateInfo = duplicates.map { (itemId, scripts) ->
        "Item ID $itemId has ${scripts.size} scripts: ${scripts.map { it::class.simpleName }}"
      }.joinToString(", ")

      throw ItemScriptValidationException(
        "Duplicate item scripts found: $duplicateInfo"
      )
    }

    // Get all consumable items
    val consumableItems = itemRepository.findItemByType(Item.ItemType.USABLE )

    LOG.debug { "Found ${consumableItems.size} consumable item(s)" }

    // Check that each consumable has a script
    val scriptItemIds = itemScripts.map { it.itemId }.toSet()
    val consumablesWithoutScript = consumableItems.filter { it.id !in scriptItemIds }

    if (consumablesWithoutScript.isNotEmpty()) {
      val missingInfo = consumablesWithoutScript.joinToString(", ") {
        "${it.identifier} (ID: ${it.id})"
      }

      throw ItemScriptValidationException(
        "Consumable items without scripts: $missingInfo"
      )
    }

    LOG.info { "Item script validation completed successfully. All ${consumableItems.size} consumable(s) have valid scripts." }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}