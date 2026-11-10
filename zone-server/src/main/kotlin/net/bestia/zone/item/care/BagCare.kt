package net.bestia.zone.item.care

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Stamina
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemOwner
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.material.ItemMaterial
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.script.ItemScriptExecutionService
import net.bestia.zone.item.script.RestorativeScript
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * A bestia looking after itself from its own bag. Runs on the tick: the live bag goes first and the database
 * follows behind, keyed by the master like every other item write.
 *
 * Only food and bandages. Potions and drinks come in glass, and those stay the master's to hand out.
 */
@Component
class BagCare(
  private val templates: ItemTemplateRegistry,
  private val scripts: ItemScriptExecutionService,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  enum class Need { STAMINA, HEALTH }

  fun hasFor(inventory: Inventory, need: Need): Boolean = bestFor(inventory, need) != null

  /** What a working bestia carries for its master: everything but what it wears and its own care items. */
  fun spareGoods(inventory: Inventory): List<Inventory.Item> {
    return inventory.getItems().filter { held ->
      !held.equipped && careScriptOf(held.itemId) == null && !templates.isBound(held.itemId)
    }
  }

  /** Uses the carried item that restores the most of [need]. False when there is none, or it changed nothing. */
  fun use(world: World, entityId: EntityId, need: Need): Boolean {
    val inventory = world.get(entityId, Inventory::class) ?: return false
    val (held, script) = bestFor(inventory, need) ?: return false

    if (!scripts.use(world, entityId, script, held.playerItemId, ScriptArgs.EMPTY)) {
      return false
    }

    persistUsed(world, entityId, held.itemId)
    return true
  }

  /** The items in [inventory] a bestia may use for [need], best first. */
  fun suppliesFor(inventory: Inventory, need: Need): List<Inventory.Item> {
    return candidatesFor(inventory, need).sortedByDescending { (_, script) -> script.restores(need) }.map { it.first }
  }

  private fun bestFor(inventory: Inventory, need: Need): Pair<Inventory.Item, RestorativeScript>? {
    return candidatesFor(inventory, need).maxByOrNull { (_, script) -> script.restores(need) }
  }

  private fun candidatesFor(inventory: Inventory, need: Need): List<Pair<Inventory.Item, RestorativeScript>> {
    return inventory.getItems()
      .filter { !it.equipped }
      .mapNotNull { held -> careScriptOf(held.itemId)?.let { held to it } }
      .filter { (_, script) -> script.restores(need) > 0 }
  }

  private fun careScriptOf(itemId: Long): RestorativeScript? {
    val template = templates.templateOf(itemId) ?: return null
    if (template.type != Item.ItemType.USABLE || template.material !in CARE_MATERIALS) return null

    return scripts.scriptOf(itemId) as? RestorativeScript
  }

  private fun RestorativeScript.restores(need: Need): Int = when (need) {
    Need.STAMINA -> stamina
    Need.HEALTH -> health
  }

  private fun persistUsed(world: World, entityId: EntityId, itemId: Long) {
    val owner = ItemOwner.of(world, entityId) ?: return
    asyncJobExecutor.submit(key = owner.masterId) {
      val spent = inventoryService.consumeAll(owner.masterId, listOf(itemId to 1), owner.playerBestiaId)
      if (!spent) {
        LOG.warn { "Bestia ${owner.playerBestiaId} of master ${owner.masterId} used item $itemId the database no longer holds" }
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** Food, and cloth for bandages. */
    private val CARE_MATERIALS = setOf(ItemMaterial.FOOD, ItemMaterial.CLOTH)

    val READS: ComponentClassSet = setOf(Inventory::class)

    /** A restorative moves the vitals and takes from the bag. */
    val WRITES: ComponentClassSet = setOf(Inventory::class, Health::class, Stamina::class)
  }
}
