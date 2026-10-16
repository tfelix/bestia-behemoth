package net.bestia.zone.spawn.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.item.persistence.ItemContainer
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent

/**
 * A killed player bestia drops its bag as kill loot, the way a mob drops its drop table. Worn gear stays on the
 * body. Like [net.bestia.zone.item.net.DropItemHandler], the database gives the items up first, so a drop can
 * lose an item but never copy one.
 */
@SpringComponent
class BestiaBagDropSystem(
  private val inventoryService: InventoryService,
  private val lootItemEntitySpawner: LootItemEntitySpawner,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val worldView: WorldView,
) : System {
  override val phase = Phase.DEATH
  override val after = setOf(DeathSystem::class)

  override val reads: ComponentClassSet = setOf(OwnedBestia::class, Position::class, TakenDamage::class, Account::class)
  override val writes: ComponentClassSet = setOf(Dead::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Dead::class, OwnedBestia::class).each { id ->
      val dead = get<Dead>()
      if (dead.bagDropped) {
        return@each
      }
      dead.bagDropped = true

      val owner = get<OwnedBestia>()
      // Taken now: a respawn moves the body and clears its damage before the job may run.
      val fellAt = world.get(id, Position::class)?.toVec3L() ?: return@each
      val killer = world.get(id, TakenDamage::class)?.topAccount(world)

      // Keyed by the master like every other write to its containers, so a loot grant still queued lands first.
      asyncJobExecutor.submit(owner.masterId) { dropBag(id, owner, fellAt, killer) }
    }
  }

  private fun dropBag(bestiaEntityId: EntityId, owner: OwnedBestia, fellAt: Vec3L, killer: Long?) {
    val removed = inventoryService.removeAllFreeFromOwner(owner.masterId, owner.playerBestiaId)
    if (removed.isEmpty()) {
      return
    }

    val dropped = worldView.modify(bestiaEntityId) { id ->
      val inventory = get(id, Inventory::class) ?: return@modify emptyList()
      removed.mapNotNull { item -> inventory.mirror(item) }
    }.orEmpty()

    if (dropped.size < removed.size) {
      LOG.warn {
        "${removed.size - dropped.size} items left bestia ${owner.playerBestiaId} in DB but not its live bag; they are not dropped"
      }
    }

    worldView.read {
      dropped.forEach { item ->
        lootItemEntitySpawner.spawnLootItem(
          this,
          itemId = item.itemId,
          amount = item.amount,
          pos = fellAt,
          uniqueId = item.uniqueId,
          lootOwner = killer
        )
      }
    }
  }

  private fun Inventory.mirror(item: ItemContainer.DetachedItem): DroppedItem? {
    val itemId = item.template.id
    val uniqueId = item.instance?.id ?: 0L
    val amount = mirrorRemoval(itemId, item.amount, uniqueId) ?: return null

    return DroppedItem(itemId, amount, uniqueId)
  }

  private data class DroppedItem(
    val itemId: Long,
    val amount: Int,
    val uniqueId: Long,
  )

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
