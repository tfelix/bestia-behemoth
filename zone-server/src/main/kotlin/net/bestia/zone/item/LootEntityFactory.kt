package net.bestia.zone.item

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.item.Loot
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.ZoneOperations
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import kotlin.random.Random

/**
 * Spawns an item entity in the world which can be used to pickup.
 */
@Component
class LootEntityFactory(
  @Lazy
  private val zoneServer: ZoneOperations,
  private val lootItemRepository: LootItemRepository
) {

  @Transactional(readOnly = true)
  fun createLootEntities(bestiaId: Long, pos: Vec3L): List<EntityId> {
    val lootItems = lootItemRepository.findAllByBestiaId(bestiaId)

    val spawnItems = lootItems.filter { lootItem ->
      val roll = Random.nextInt(1, 1_001) // 1 to 100000 inclusive

      roll <= lootItem.dropChance
    }

    LOG.debug { "Spawning loot $spawnItems for bestia $bestiaId ($lootItems) on pos $pos" }

    return spawnItems.map { spawnItem ->
      zoneServer.addEntityWithWriteLock {
        it.addAll(
          Position.fromVec3(pos),
          Loot(
            itemId = spawnItem.item.id,
            amount = 1,
          )
        )
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}