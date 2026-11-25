package net.bestia.zone.spawn.ecs

import net.bestia.zone.item.ItemProvenance
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
import net.bestia.zone.item.keepsake.KeepsakeBags
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.item.persistence.ItemContainer
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent

/**
 * A killed player bestia drops its bag as kill loot, the way a mob drops its drop table. Worn gear stays on the
 * body. Under a Keepsake Ward the bag drops as one warded bundle instead, see [KeepsakeBags]. Like
 * [net.bestia.zone.item.net.DropItemHandler], the database gives the items up first, so a drop can lose an item but
 * never copy one.
 */
@SpringComponent
class BestiaBagDropSystem(
  private val inventoryService: InventoryService,
  private val lootItemEntitySpawner: LootItemEntitySpawner,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val worldView: WorldView,
  private val keepsakes: KeepsakeBags,
) : System {
  override val phase = Phase.DEATH
  override val after = setOf(DeathSystem::class)

  override val reads: ComponentClassSet = setOf(OwnedBestia::class, Position::class, TakenDamage::class, Account::class)
  override val writes: ComponentClassSet = setOf(Dead::class, StatusEffects::class)

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
      val warded = world.get(id, StatusEffects::class)?.removeEffect(StatusEffectId.KEEPSAKE_WARD.id) == true

      // Keyed by the master like every other write to its containers, so a loot grant still queued lands first.
      asyncJobExecutor.submit(owner.masterId) { dropBag(id, owner, fellAt, killer, warded) }
    }
  }

  private fun dropBag(bestiaEntityId: EntityId, owner: OwnedBestia, fellAt: Vec3L, killer: Long?, warded: Boolean) {
    val packed = if (warded) keepsakes.pack(owner) else null
    val removed = packed?.removed ?: inventoryService.removeAllFreeFromOwner(owner.masterId, owner.playerBestiaId)
    if (removed.isEmpty()) {
      return
    }

    // A bestia lost for good has left the world already: no live bag is left to mirror, the database's word stands.
    val dropped = worldView.modify(bestiaEntityId) { id ->
      val inventory = get(id, Inventory::class) ?: return@modify emptyList()
      removed.mapNotNull { item -> inventory.mirror(item) }
    } ?: removed.map { it.toDropped(it.amount) }

    if (dropped.size < removed.size) {
      LOG.warn {
        "${removed.size - dropped.size} items left bestia ${owner.playerBestiaId} in DB but not its live bag; they are not dropped"
      }
    }

    val bundle = packed?.bundle
    if (bundle != null) {
      worldView.read { keepsakes.drop(this, bundle, owner.masterId, fellAt) }
      return
    }

    worldView.read {
      dropped.forEach { item ->
        lootItemEntitySpawner.spawnLootItem(
          this,
          itemId = item.itemId,
          amount = item.amount,
          pos = fellAt,
          uniqueId = item.uniqueId,
          lootOwner = killer,
          provenance = item.provenance
        )
      }
    }
  }

  private fun Inventory.mirror(item: ItemContainer.DetachedItem): DroppedItem? {
    val amount = mirrorRemoval(item.template.id, item.amount, item.instance?.id ?: 0L) ?: return null

    return item.toDropped(amount)
  }

  private fun ItemContainer.DetachedItem.toDropped(amount: Int): DroppedItem {
    return DroppedItem(template.id, amount, instance?.id ?: 0L, instance?.provenance ?: provenance)
  }

  private data class DroppedItem(
    val itemId: Long,
    val amount: Int,
    val uniqueId: Long,
    val provenance: ItemProvenance?,
  )

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
