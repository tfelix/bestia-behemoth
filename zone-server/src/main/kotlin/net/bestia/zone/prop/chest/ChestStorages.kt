package net.bestia.zone.prop.chest

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.ecs.ChestStorage
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.SpilledProperty
import net.bestia.zone.item.ecs.toLiveInventory
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.persistence.PlayerStructureRepository
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * What every chest holds, live, whether its prop stands in the world or not. Chests are few, so all are loaded
 * at boot, and a chest's prop gets the same [Inventory] each time it spawns. Tick thread only, like
 * [PlayerStructureRegistry].
 */
@Service
class ChestStorages(
  private val structures: PlayerStructureRegistry,
  private val rows: PlayerStructureRepository,
  private val itemStats: ItemStatCatalog,
  private val lootItems: LootItemEntitySpawner,
  private val worldView: WorldView,
) {

  private val contentsByStructureId = HashMap<Long, Inventory>()

  /** Boot-time only, before the tick loop starts, and inside a transaction: a container's slots are lazy. */
  fun loadAll() {
    rows.findAllByKind(StaticEntityKind.CHEST).forEach { row ->
      row.storage?.let { contentsByStructureId[row.id] = it.toLiveInventory(itemStats) }
    }

    LOG.info { "Loaded the contents of ${contentsByStructureId.size} chest(s)" }
  }

  /** Gives the prop [entityId] of the chest [structureId] its owner and its contents. */
  fun furnish(world: World, entityId: EntityId, structureId: Long) {
    val entry = structures.of(structureId) ?: return

    world.add(entityId, ChestStorage(structureId, entry.ownerMasterId, entry.ownerAccountId))
    world.add(entityId, contentsOf(structureId))
  }

  /** A chest placed after boot starts empty. */
  fun contentsOf(structureId: Long): Inventory {
    return contentsByStructureId.getOrPut(structureId) { Inventory(mutableListOf()) }
  }

  /**
   * Drops what a fallen chest held at [at], for anyone but [ownerAccountId] to steal. Its container goes with the
   * structure row; the copies it held live on as ground items. Posted, so the drops happen outside the system
   * that saw the chest fall.
   */
  fun spill(structureId: Long, ownerAccountId: AccountId?, at: Vec3L) {
    val contents = contentsByStructureId.remove(structureId)?.getItems() ?: return

    worldView.post {
      contents.forEach { item ->
        val lying = lootItems.spawnLootItem(
          this, itemId = item.itemId, amount = item.amount, pos = at, uniqueId = item.uniqueId,
          provenance = item.provenance,
        )
        add(lying, SpilledProperty(itemLevelOf(item), ownerAccountId))
      }
    }

    if (contents.isNotEmpty()) LOG.info { "Chest $structureId spilled ${contents.size} pile(s) at $at" }
  }

  /** A pile carries no level of its own; its level is its item's. */
  private fun itemLevelOf(item: Inventory.Item): Int {
    return if (item.itemLevel > 0) item.itemLevel else itemStats.itemLevelOf(item.itemId)
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
