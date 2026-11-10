package net.bestia.zone.prop.chest

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.item.ecs.ChestStorage
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.toLiveInventory
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.persistence.PlayerStructureRepository
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

  fun forget(structureId: Long) {
    contentsByStructureId.remove(structureId)
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
