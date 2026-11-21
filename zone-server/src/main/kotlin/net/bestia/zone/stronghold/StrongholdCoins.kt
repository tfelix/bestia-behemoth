package net.bestia.zone.stronghold

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.WorldReserve
import net.bestia.zone.entity.GameMetrics.CoinSource
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * What members pay for their Stronghold, planting and investing alike. The coins go back to the world reserve.
 * Tick thread only.
 */
@Component
class StrongholdCoins(
  private val inventoryService: InventoryService,
  private val commodities: CommodityItems,
  private val reserve: WorldReserve,
  private val world: WorldView,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  fun heldBy(world: World, entityId: EntityId): Long {
    val coin = commodities.coinItemId() ?: return 0
    val inventory = world.get(entityId, Inventory::class) ?: return 0

    return inventory.getItems().filter { it.itemId == coin && it.isStackable }.sumOf { it.amount.toLong() }
  }

  /**
   * Takes [coins] off the live bag now and out of the database in a job, as a shop trade does. They go back to
   * the world reserve once the database agrees.
   *
   * @return false when the bag does not hold them
   */
  fun pay(world: World, entityId: EntityId, masterId: Long, coins: Long): Boolean {
    if (coins == 0L) return true

    val coin = commodities.coinItemId() ?: return false
    val inventory = world.get(entityId, Inventory::class) ?: return false
    val amount = coins.toInt()
    inventory.takeAcrossPiles(coin, amount) ?: return false

    asyncJobExecutor.submit(masterId) {
      if (inventoryService.consumeAll(masterId, listOf(coin to amount))) {
        this.world.read { reserve.charge(-coins.toDouble(), CoinSource.STRONGHOLD) }
      } else {
        // The live bag is put right by the next login; what was paid for already happened, so it came free.
        LOG.warn { "Master $masterId paid $coins coins for a Stronghold, but the database still holds them" }
      }
    }

    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
