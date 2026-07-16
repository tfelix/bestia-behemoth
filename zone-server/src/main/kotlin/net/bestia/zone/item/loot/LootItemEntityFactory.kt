package net.bestia.zone.item.loot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.item.ItemVisual
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.persistence.Persistent
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import kotlin.random.Random

/**
 * Spawns an item entity in the world which can be used to pickup.
 */
@Component
class LootItemEntityFactory(
  private val lootItemRepository: LootItemRepository
) {

  @Transactional(readOnly = true)
  fun createLootEntities(world: WorldView, bestiaId: Long, pos: Vec3L): List<EntityId> {
    val lootItems = lootItemRepository.findAllByBestiaId(bestiaId)

    val spawnItems = lootItems.filter { lootItem ->
      val roll = Random.nextInt(1, 1_0001) // 1 to 10000 inclusive

      roll <= lootItem.dropChance
    }

    LOG.debug { "Spawning loot $spawnItems from bestia $bestiaId ($lootItems) on pos $pos" }

    return spawnItems.map { spawnItem ->
      createLootEntity(world, itemId = spawnItem.item.id, amount = 1, pos = pos)
    }
  }

  /**
   * Spawns a single ground item entity at the given position which can be picked up.
   */
  fun createLootEntity(
    world: WorldView,
    itemId: Long,
    amount: Int,
    pos: Vec3L,
    playerItemUniqueId: Long = 0,
    entityId: EntityId? = null,
  ): EntityId {
    val configure: World.(EntityId) -> Unit = { id ->
      add(id, Position.fromVec3(pos))
      add(
        id,
        ItemVisual(
          itemId = itemId,
          amount = amount,
          playerItemId = playerItemUniqueId
        )
      )
      add(id, Persistent)
    }

    // Rehydrated ground items keep their persisted id; freshly dropped ones get a new one.
    return if (entityId != null) world.createEntity(entityId, configure) else world.createEntity(configure)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}