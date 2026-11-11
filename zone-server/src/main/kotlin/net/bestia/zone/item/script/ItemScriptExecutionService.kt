package net.bestia.zone.item.script

import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.script.ScriptArgs
import org.springframework.stereotype.Service

@Service
class ItemScriptExecutionService(
  itemScripts: List<ItemScript>
) {

  private val itemScriptsById = itemScripts.associateBy { it.itemId }

  /**
   * Executes [item]'s script for the given user and, on success, consumes one from the held item
   * [playerItemId] names in the user's live ECS [Inventory]. Returns true if an item was consumed, so the caller can persist the matching
   * DB decrement (this service deliberately stays ECS-only, like the rest of the obtain/consume
   * pipeline).
   */
  fun useItem(world: World, userId: EntityId, item: Item, playerItemId: Long, args: ScriptArgs): Boolean {
    val itemScript = itemScriptsById[item.id]
      ?: throw ItemScriptNotFoundException(item)

    return use(world, userId, itemScript, playerItemId, args)
  }

  /** The script bound to [itemId], or null for an item that does nothing when used. */
  fun scriptOf(itemId: Long): ItemScript? = itemScriptsById[itemId]

  /** As [useItem], for a caller on the tick that holds a script rather than a catalogue row. */
  fun use(world: World, userId: EntityId, itemScript: ItemScript, playerItemId: Long, args: ScriptArgs): Boolean {
    val uniqueId = world.get(userId, Inventory::class)?.byPlayerItemId(playerItemId)?.uniqueId ?: 0L
    val isSuccess = itemScript.executeHeld(world, userId, args, uniqueId)

    if (isSuccess) {
      val inventory = world.getOrThrow(userId, Inventory::class)
      inventory.take(playerItemId, 1)
    }

    return isSuccess
  }
}
