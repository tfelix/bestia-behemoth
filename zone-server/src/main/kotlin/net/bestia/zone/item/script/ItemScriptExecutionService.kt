package net.bestia.zone.item.script

import net.bestia.zone.ecs.Entity
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.network.IsDirty
import net.bestia.zone.item.Item
import org.springframework.stereotype.Service

@Service
class ItemScriptExecutionService(
  itemScripts: List<ItemScript>
) {

  private val itemScriptsById = itemScripts.associateBy { it.itemId }

  fun useItem(entity: Entity, item: Item) {
    val itemScript = itemScriptsById[item.id]
      ?: throw ItemScriptNotFoundException(item)

    val isSuccess = itemScript.execute(entity)

    if (isSuccess) {
      val inventory = entity.getOrThrow(Inventory::class)
      inventory.decItem(item.id.toInt())
      entity.add(IsDirty)
    }
  }
}