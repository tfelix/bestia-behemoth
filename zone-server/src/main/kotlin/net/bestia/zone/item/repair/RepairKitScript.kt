package net.bestia.zone.item.repair

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemOwner
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.script.ItemScript
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.script.ScriptArgKeys
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId

/**
 * Using a repair kit restores the full durability of one held or worn item of its kind, wherever its owner stands.
 * Unlike a repair at the forge it never fails, which is what the higher price pays for.
 */
class RepairKitScript(
  private val kit: RepairKit,
  private val templates: ItemTemplateRegistry,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
) : ItemScript {

  override val itemId: Long = kit.itemId

  override fun execute(world: World, userId: EntityId, args: ScriptArgs): Boolean {
    val inventory = world.get(userId, Inventory::class) ?: return false
    val owner = ItemOwner.of(world, userId) ?: return false
    val target = args.long(ScriptArgKeys.TARGET_ITEM)?.let(inventory::byPlayerItemId) ?: return false
    val worn = wornCopyOf(world, userId, target)
    // A worn copy's wear lives on the slot; the bag's mirror of it is only brought up to date on taking it off.
    val durability = worn?.item?.durability ?: target.durability

    // The window offers only the items that pass, so a refusal here is a stale or forged request.
    if (!accepts(target, durability)) {
      LOG.debug { "Entity $userId cannot use the $kit repair kit on item ${target.uniqueId}" }
      return false
    }

    inventory.mirrorWear(target.uniqueId, target.maxDurability, target.maxDurability)
    if (worn != null) {
      worn.equipment.replace(worn.slot, worn.item.copy(durability = worn.item.maxDurability))
      world.add(userId, IsStatusValueDirty)
    }
    asyncJobExecutor.submit(owner.masterId) {
      inventoryService.repair(owner.masterId, target.uniqueId, owner.playerBestiaId)
    }

    return true
  }

  /** The rules of a forge repair: a single copy of this kit's kind that wears and has worn. */
  fun accepts(target: Inventory.Item, durability: Int): Boolean {
    val template = templates.templateOf(target.itemId) ?: return false
    val isCopy = target.uniqueId != 0L && !template.stackable

    return isCopy && kit.mends(template) && target.maxDurability > 0 && durability < target.maxDurability
  }

  private fun wornCopyOf(world: World, wearerId: EntityId, target: Inventory.Item): WornCopy? {
    if (!target.equipped) return null
    val equipment = world.get(wearerId, Equipment::class) ?: return null
    val (slot, item) = equipment.getWorn().entries.firstOrNull { it.value.uniqueId == target.uniqueId } ?: return null

    return WornCopy(equipment, slot, item)
  }

  private class WornCopy(val equipment: Equipment, val slot: EquipmentSlot, val item: Equipment.EquippedItem)

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
