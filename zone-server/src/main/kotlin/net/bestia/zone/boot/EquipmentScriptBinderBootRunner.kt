package net.bestia.zone.boot

import net.bestia.zone.battle.status.EquipmentScriptRegistry
import net.bestia.zone.item.Item
import net.bestia.zone.item.ItemRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Resolves every equip item's `script` name to its bean once the item import has run, so the
 * per-tick lookup in `StatusValueRecalcSystem` never has to touch the database.
 *
 * Runs after the item import (order 100) and before [net.bestia.zone.item.script.ItemScriptValidator]
 * (order 200), which then verifies that no equip item names a script that failed to resolve.
 */
@Component
@Order(150)
class EquipmentScriptBinderBootRunner(
  private val itemRepository: ItemRepository,
  private val equipmentScriptRegistry: EquipmentScriptRegistry,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    equipmentScriptRegistry.bind(itemRepository.findItemByType(Item.ItemType.EQUIP))
  }
}
