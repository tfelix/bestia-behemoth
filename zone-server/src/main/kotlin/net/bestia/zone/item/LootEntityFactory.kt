package net.bestia.zone.item

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.item.ItemVisual
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.core.EntityId
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import kotlin.random.Random

/**
 * Spawns an item entity in the world which can be used to pickup.
 */
@Component
class LootEntityFactory(
  private val world: WorldView,
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
      createLootEntity(itemId = spawnItem.item.id, amount = 1, pos = pos)
    }
  }

  /**
   * Spawns a single ground item entity at the given position which can be picked up.
   */
  fun createLootEntity(itemId: Long, amount: Int, pos: Vec3L, uniqueId: Long = 0): EntityId {
    return world.createEntity { id ->
      add(id, Position.fromVec3(pos))
      add(
        id,
        ItemVisual(
          itemId = itemId,
          amount = amount,
          uniqueId = uniqueId
        )
      )
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
