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
 * By default only food and bandages. Potions and drinks come in glass, and those stay the master's to hand out,
 * unless the bestia's [CareItems] allow them.
 */
@Component
class BagCare(
  private val templates: ItemTemplateRegistry,
  private val scripts: ItemScriptExecutionService,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  enum class Need { STAMINA, HEALTH }

  fun hasFor(inventory: Inventory, need: Need, items: CareItems = CareItems()): Boolean {
    return bestFor(inventory, need, items) != null
  }

  /** What a working bestia carries for its master: everything but what it wears and its own care items. */
  fun spareGoods(inventory: Inventory, items: CareItems = CareItems()): List<Inventory.Item> {
    return inventory.getItems().filter { held ->
      !held.equipped && careScriptOf(held.itemId, items) == null && !templates.isBound(held.itemId)
    }
  }

  /** Uses the carried item that restores the most of [need]. False when there is none, or it changed nothing. */
  fun use(world: World, entityId: EntityId, need: Need, items: CareItems = CareItems()): Boolean {
    val inventory = world.get(entityId, Inventory::class) ?: return false
    val (held, script) = bestFor(inventory, need, items) ?: return false

    if (!scripts.use(world, entityId, script, held.playerItemId, ScriptArgs.EMPTY)) {
      return false
    }

    persistUsed(world, entityId, held.itemId)
    return true
  }

  /** How much stamina the food in [inventory] would give, all eaten: what a worker has left to work on. */
  fun staminaStockOf(inventory: Inventory, items: CareItems = CareItems()): Int {
    return candidatesFor(inventory, Need.STAMINA, items).sumOf { (held, script) -> held.amount * script.stamina }
  }

  /** The items in [inventory] a bestia may use for [need], best first. */
  fun suppliesFor(inventory: Inventory, need: Need, items: CareItems = CareItems()): List<Inventory.Item> {
    return candidatesFor(inventory, need, items)
      .sortedByDescending { (_, script) -> script.restores(need) }
      .map { it.first }
  }

  private fun bestFor(inventory: Inventory, need: Need, items: CareItems): Pair<Inventory.Item, RestorativeScript>? {
    return candidatesFor(inventory, need, items).maxByOrNull { (_, script) -> script.restores(need) }
  }

  private fun candidatesFor(
    inventory: Inventory,
    need: Need,
    items: CareItems,
  ): List<Pair<Inventory.Item, RestorativeScript>> {
    return inventory.getItems()
      .filter { !it.equipped }
      .mapNotNull { held -> careScriptOf(held.itemId, items)?.let { held to it } }
      .filter { (_, script) -> script.restores(need) > 0 }
  }

  private fun careScriptOf(itemId: Long, items: CareItems): RestorativeScript? {
    if (itemId in items.neverUse) return null
    if (itemId !in items.alsoUse && !isCommonCare(itemId)) return null

    return scripts.scriptOf(itemId) as? RestorativeScript
  }

  private fun isCommonCare(itemId: Long): Boolean {
    val template = templates.templateOf(itemId) ?: return false

    return template.type == Item.ItemType.USABLE && template.material in CARE_MATERIALS
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
