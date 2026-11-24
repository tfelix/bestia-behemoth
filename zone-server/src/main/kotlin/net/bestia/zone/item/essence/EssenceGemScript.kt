package net.bestia.zone.item.essence

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.container.InventoryService
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
 * Using an Essence Gem puts its essence into a held item for good. The gem is used up, and nothing takes the
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
    asyncJobExecutor.submit(owner.masterId) {
      inventoryService.updateInstance(owner.masterId, target.uniqueId, owner.playerBestiaId) { it.socket(essence) }
    }

    return true
  }

  /**
   * A free socket of the essence's group, in an item at least as high as its gem asks. The item has to be in the
   * bag: worn gear would need its wearer's stats worked out again.
   */
  fun accepts(target: Inventory.Item, essence: Essence): Boolean {
    val template = templates.templateOf(target.itemId) ?: return false
    val group = species.of(essence.bestiaId)?.group ?: return false
    val hasFreeSocket = target.essences.size < target.slots

    return !target.equipped && hasFreeSocket && SocketGroup.of(template) == group &&
      target.itemLevel >= essence.tier.minItemLevel
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** `essence_gem` in items.yml. */
    const val ITEM_ID = 141L
  }
}
