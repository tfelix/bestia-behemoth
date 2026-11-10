package net.bestia.zone.treasure

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.treasure.TreasureCache
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * Draws treasure maps and crumbles them once their cache is dug. A map is drawn in an async job, like a chart,
 * and then mirrored onto the live bag; crumbling happens on the tick, where the cache was opened.
 */
@Service
class TreasureMapService(
  private val minter: TreasureMapMinter,
  private val registry: TreasureMapRegistry,
  private val items: ItemTemplateRegistry,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val outMessageProcessor: OutMessageProcessor,
  private val world: WorldView,
) {

  fun draw(entityId: EntityId, masterId: Long, accountId: Long, cache: TreasureCache, centreX: Double, centreY: Double, radius: Double) {
    asyncJobExecutor.submit(masterId) {
      val minted = minter.mint(masterId, cache.id, centreX, centreY, radius)
      if (minted == null) {
        outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(OpError.CHART_NEEDS_BLANK))
        return@submit
      }

      registry.put(minted.record)
      showInBag(entityId, minted)
    }
  }

  /** Every map of the cache turns into a crumbled map, so its holder knows the treasure is gone. */
  fun crumble(world: World, cacheId: Long) {
    val copies = registry.takeAllOf(cacheId)
    if (copies.isEmpty()) return

    val crumbledId = items.idOf(TreasureMapMinter.CRUMBLED_TREASURE_MAP)
    if (crumbledId != null) {
      val weight = items.weightOf(crumbledId) ?: 0
      world.query(Inventory::class).each { _ ->
        val inventory = get<Inventory>()
        for (copy in copies) {
          if (inventory.removeByUniqueId(copy)) {
            inventory.addItem(Inventory.Item(crumbledId, amount = 1, weight = weight, uniqueId = copy, stackable = false))
          }
        }
      }
    }

    asyncJobExecutor.submit(JOB_KEY) {
      minter.crumble(copies)
    }
  }

  private fun showInBag(entityId: EntityId, minted: TreasureMapMinter.Minted) {
    world.modify(entityId) { id ->
      val inventory = get(id, Inventory::class)
      if (inventory == null) {
        LOG.debug { "Entity $entityId has no inventory to show treasure map ${minted.record.itemInstanceId} in" }
        return@modify
      }

      inventory.decItem(minted.blankItemId.toInt())
      inventory.addItem(
        Inventory.Item(
          itemId = minted.item.id,
          amount = 1,
          weight = minted.item.weight,
          uniqueId = minted.record.itemInstanceId,
          stackable = false
        )
      )
    }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
    const val JOB_KEY = "treasure-maps"
  }
}
