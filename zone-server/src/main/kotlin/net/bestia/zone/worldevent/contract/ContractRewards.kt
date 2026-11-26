package net.bestia.zone.worldevent.contract

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.honor.HonorAct
import net.bestia.zone.honor.HonorService
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.world.SettlementLoreService
import net.bestia.zone.world.WorldService
import net.bestia.zone.worldevent.WorldEventEntry
import net.bestia.zone.worldevent.persistence.WorldEventInstance
import org.springframework.stereotype.Service
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Pays everyone who helped end a world event: no coin, only its loot, split by share, and honor. The loot lands at
 * the feet of each contributor who is online, kept for them alone; the town nearest the event hears of every one.
 */
@Service
class ContractRewards(
  private val ledger: ContributionLedger,
  private val itemTemplates: ItemTemplateRegistry,
  private val lootSpawner: LootItemEntitySpawner,
  private val honor: HonorService,
  private val worldService: WorldService,
  private val world: WorldView,
) {

  fun grant(
    key: ContributionLedger.Key,
    entry: WorldEventEntry,
    instance: WorldEventInstance,
    act: HonorAct = HonorAct.DEFEND_SETTLEMENT,
  ) {
    val shares = ledger.sharesOf(key).filter { (_, share) -> share >= MIN_SHARE }
    ledger.forget(key)
    if (shares.isEmpty()) return

    val town = nearestTown(instance.x.toDouble(), instance.y.toDouble())
    val local = honor.localAmountOf(act)
    shares.forEach { (who, _) -> town?.let { honor.changeSettlement(who.masterId, who.masterName, it, local) } }

    val byMaster = shares.associate { (who, share) -> who.masterId to share }
    world.read {
      val online = ArrayList<Online>()
      query(Master::class, Position::class, Account::class).each { id ->
        val share = byMaster[get<Master>().masterId] ?: return@each
        online += Online(id, get<Position>().toVec3L(), get<Account>().accountId, share)
      }

      online.forEach { who ->
        honor.rewardGlobal(this, act, who.entity)
        entry.loot.forEach { pile ->
          val itemId = itemTemplates.idOf(pile.item) ?: return@forEach
          val amount = (pile.amount * who.share).roundToInt().coerceAtLeast(1)
          lootSpawner.spawnLootItem(this, itemId, amount, who.at, lootOwner = who.account)
        }
      }
    }

    LOG.info { "Rewarded ${shares.size} contributor(s) of ${entry.id}" }
  }

  private class Online(val entity: Long, val at: Vec3L, val account: Long, val share: Double)

  private fun nearestTown(x: Double, y: Double): Int? =
    SettlementLoreService.settlementPositions(worldService.generated)
      .minByOrNull { (_, at) -> hypot(at.x - x, at.y - y) }
      ?.key

  private companion object {
    val LOG = KotlinLogging.logger { }

    /** Below a twentieth of the work, a master happened to pass by. */
    const val MIN_SHARE = 0.05
  }
}
