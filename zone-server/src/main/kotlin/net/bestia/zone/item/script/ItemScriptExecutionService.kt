package net.bestia.zone.item.script

import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.core.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.Item
import org.springframework.stereotype.Service

@Service
class ItemScriptExecutionService(
  itemScripts: List<ItemScript>
) {

  private val itemScriptsById = itemScripts.associateBy { it.itemId }

  fun useItem(world: World, userId: EntityId, item: Item) {
    val itemScript = itemScriptsById[item.id]
      ?: throw ItemScriptNotFoundException(item)

    val isSuccess = itemScript.execute(world, userId)

    if (isSuccess) {
      val inventory = world.getOrThrow(userId, Inventory::class)
      inventory.decItem(item.id.toInt())
      world.markChanged<Inventory>(userId)
    }
  }
}
