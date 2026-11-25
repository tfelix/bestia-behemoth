package net.bestia.zone.item.essence

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemOwner
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.script.ItemScript
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.script.ScriptArgKeys
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Using an Essence Gem puts its essence into a held or worn item for good. The gem is used up, and nothing takes the
 * essence out again.
 */
@Component
class EssenceGemScript(
  private val templates: ItemTemplateRegistry,
  private val species: SpeciesEssences,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
) : ItemScript {

  override val itemId: Long = ITEM_ID

  override fun execute(world: World, userId: EntityId, args: ScriptArgs): Boolean {
    return false
  }

  override fun executeHeld(world: World, userId: EntityId, args: ScriptArgs, uniqueId: Long): Boolean {
    val inventory = world.get(userId, Inventory::class) ?: return false
    val owner = ItemOwner.of(world, userId) ?: return false
    val essence = inventory.getItems().firstOrNull { it.uniqueId == uniqueId && uniqueId != 0L }?.heldEssence
      ?: return false
    val target = args.long(ScriptArgKeys.TARGET_ITEM)?.let(inventory::byPlayerItemId) ?: return false

    // The window offers only the items that pass, so a refusal here is a stale or forged request.
    if (!accepts(target, essence)) {
      LOG.debug { "Entity $userId cannot put $essence into item ${target.uniqueId}" }
      return false
    }

    inventory.socket(target.uniqueId, essence)
    if (target.equipped) {
      socketWorn(world, userId, target.uniqueId, essence)
    }
    asyncJobExecutor.submit(owner.masterId) {
      inventoryService.socket(owner.masterId, target.uniqueId, owner.playerBestiaId, essence)
    }

    return true
  }

  /** A free socket of the essence's group, in an item high enough for its gem and, for a boss essence, for the boss. */
  fun accepts(target: Inventory.Item, essence: Essence): Boolean {
    val template = templates.templateOf(target.itemId) ?: return false
    val definition = species.of(essence.bestiaId) ?: return false
    val hasFreeSocket = target.essences.size < target.slots
    val fitsItsTool = definition.tool == null || definition.tool == template.toolKind

    return hasFreeSocket && SocketGroup.of(template) == definition.group && fitsItsTool &&
      target.itemLevel >= minItemLevelOf(essence)
  }

  private fun minItemLevelOf(essence: Essence): Int {
    val bossFloor = species.bossLevelOf(essence.bestiaId)?.let { it - BOSS_LEVEL_MARGIN } ?: 0

    return maxOf(essence.tier.minItemLevel, bossFloor)
  }

  private fun socketWorn(world: World, wearerId: EntityId, uniqueId: Long, essence: Essence) {
    val equipment = world.get(wearerId, Equipment::class) ?: return
    val (slot, worn) = equipment.getWorn().entries.firstOrNull { it.value.uniqueId == uniqueId } ?: return

    equipment.replace(slot, worn.copy(essences = worn.essences + essence))
    world.add(wearerId, IsStatusValueDirty)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** A boss essence goes only into an item at most this many levels below the boss. */
    const val BOSS_LEVEL_MARGIN = 20

    /** `essence_gem` in items.yml. */
    const val ITEM_ID = 141L
  }
}
