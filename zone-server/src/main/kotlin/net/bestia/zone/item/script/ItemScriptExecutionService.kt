package net.bestia.zone.item.script

import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.Item
import org.springframework.stereotype.Service

@Service
class ItemScriptExecutionService(
  itemScripts: List<ItemScript>
) {

  private val itemScriptsById = itemScripts.associateBy { it.itemId }

  /**
   * Executes [item]'s script for the given user and, on success, consumes one from the user's live
   * ECS [Inventory]. Returns true if an item was consumed, so the caller can persist the matching
   * DB decrement (this service deliberately stays ECS-only, like the rest of the obtain/consume
   * pipeline).
   */
  fun useItem(world: World, userId: EntityId, item: Item): Boolean {
    val itemScript = itemScriptsById[item.id]
      ?: throw ItemScriptNotFoundException(item)

    val isSuccess = itemScript.execute(world, userId)

    if (isSuccess) {
      val inventory = world.getOrThrow(userId, Inventory::class)
      inventory.decItem(item.id.toInt())
    }

    return isSuccess
  }
}
