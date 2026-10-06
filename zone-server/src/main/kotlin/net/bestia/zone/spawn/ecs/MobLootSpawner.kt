package net.bestia.zone.spawn.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.bestia.loot.LootDrop
import net.bestia.zone.bestia.persistence.LootItemRepository
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import kotlin.random.Random

/** Rolls a killed mob's drop table and puts what drops on the ground. */
@Component
class MobLootSpawner(
  private val lootItemRepository: LootItemRepository,
  private val lootItemEntitySpawner: LootItemEntitySpawner,
) {

  // Read once: drops are imported at boot and never change, and every kill on the tick reads them.
  private val dropsBySpecies: Map<Long, List<LootDrop>> by lazy {
    lootItemRepository.findAllDrops().groupBy { it.bestiaId }
  }

  /** Loads the drop table now, at boot, so the first kill on the tick does not reach the database. */
  fun warmUp() {
    dropsBySpecies.size
  }

  fun spawnLoot(world: World, bestiaId: Long, pos: Vec3L): List<EntityId> {
    val lootItems = dropsBySpecies[bestiaId] ?: emptyList()

    val spawnItems = lootItems.filter { lootItem ->
      val roll = Random.nextInt(1, 1_0001) // 1 to 10000 inclusive

      roll <= lootItem.dropChance
    }

    LOG.debug { "Spawning loot $spawnItems from bestia $bestiaId ($lootItems) on pos $pos" }

    return spawnItems.map { spawnItem ->
      lootItemEntitySpawner.spawnLootItem(world, itemId = spawnItem.itemId, amount = 1, pos = pos)
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
