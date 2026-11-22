package net.bestia.zone.stronghold

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.WorldReserve
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.GameMetrics.CoinSource
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ItemHolder
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.ecs.SlotStorage
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.skill.SkillId
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import kotlin.math.min

/**
 * The guild window's Stronghold tab, from anywhere: the guild's Strongholds with their waiting treasure, and the
 * leader collecting it into the guild storage. Tick thread only.
 */
@Service
class StrongholdTab(
  private val strongholds: StrongholdRegistry,
  private val seeds: EmpyriteSeedRegistry,
  private val empyrites: Empyrites,
  private val watchers: Watchers,
  private val treasury: Treasury,
  private val storages: GuildStorages,
  private val inventoryService: InventoryService,
  private val itemTemplates: ItemTemplateRegistry,
  private val commodities: CommodityItems,
  private val reserve: WorldReserve,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val messages: OutMessageProcessor,
  private val config: StrongholdConfig,
  private val clock: Clock = Clock.systemUTC(),
) {

  fun show(world: World, masterEntityId: EntityId) {
    val accountId = world.get(masterEntityId, Account::class)?.accountId ?: return
    val membership = world.get(masterEntityId, GuildMembership::class) ?: return
    val held = strongholds.heldBy(membership.guildId).sortedBy { it.rank }

    val tab = StrongholdTabSMSG(held.map(::cardOf), membership.isLeader, storageOf(membership, held))
    messages.sendToPlayer(accountId, tab)
  }

  /**
   * The leader takes every waiting drop of the Stronghold on [rank] into the guild storage, or none when it has no
   * room for all of them. The coins leave the world reserve now, as far as it holds them.
   */
  fun collect(world: World, masterEntityId: EntityId, rank: Int): OpError? {
    val membership = world.get(masterEntityId, GuildMembership::class)
    val masterId = world.get(masterEntityId, Master::class)?.masterId
    val stronghold = strongholds.on(rank)?.takeIf { it.guildId == membership?.guildId }

    // The tab offers the button only to the leader, and only with drops waiting.
    if (membership == null || masterId == null || stronghold == null || !membership.isLeader || stronghold.drops.isEmpty()) {
      return OpError.REQUEST_REFUSED
    }
    if (membership.skillLevel(SkillId.GUILD_STORAGE.name) <= 0) return OpError.GUILD_STORAGE_NOT_LEARNED

    val coins = min(stronghold.drops.sumOf { it.coins }, reserve.available().toLong())
    val goods = HashMap<Long, Int>()
    stronghold.drops.forEach { drop -> drop.goods.forEach { (itemId, amount) -> goods.merge(itemId, amount, Int::plus) } }
    commodities.coinItemId()?.takeIf { coins > 0 }?.let { goods.merge(it, coins.toInt(), Int::plus) }

    val contents = storages.contentsOf(membership.guildId)
    val piles = goods.map { (itemId, amount) -> Inventory.Item(itemId, amount, itemTemplates.templateOf(itemId)?.weight ?: 0) }
    if (SlotStorage.whatFits(contents, piles, storages.slotsOf(membership)).size < piles.size) return OpError.STORAGE_FULL

    reserve.charge(coins.toDouble(), CoinSource.STRONGHOLD)
    contents.addItems(piles)
    strongholds.put(stronghold.copy(drops = emptyList()))

    val holder = ItemHolder.GuildStorage(membership.guildId)
    asyncJobExecutor.submit(holder) { inventoryService.grantStacksTo(masterId, holder, goods) }

    return null
  }

  private fun cardOf(stronghold: Stronghold): StrongholdTabSMSG.Card {
    val now = clock.instant()
    val nextDrop = Duration.between(now, treasury.nextDropAt(stronghold)).seconds.coerceAtLeast(0)

    return StrongholdTabSMSG.Card(
      rank = stronghold.rank,
      name = seeds.awake().firstOrNull { it.rank == stronghold.rank }?.name.orEmpty(),
      isSite = stronghold.state == State.SITE,
      hp = stronghold.empyriteHp,
      maxHp = empyrites.maxHpOf(stronghold),
      defense = stronghold.progressOf(StrongholdTrack.DEFENSE).level,
      storage = stronghold.progressOf(StrongholdTrack.STORAGE).level,
      economy = stronghold.progressOf(StrongholdTrack.ECONOMY).level,
      hasSteward = stronghold.hasSteward,
      watchers = stronghold.watchers.values.sum(),
      watcherPosts = watchers.postsOf(stronghold),
      drops = stronghold.drops,
      maxDrops = config.treasuryWaitingDrops,
      nextDropSeconds = nextDrop,
    )
  }

  /** Sealed while no Stronghold of the guild has a steward to open it at. */
  private fun storageOf(membership: GuildMembership, held: List<Stronghold>): StrongholdTabSMSG.Storage {
    val used = storages.contentsOf(membership.guildId).size()
    val sealed = held.none { it.state == State.STANDING && it.hasSteward }

    return StrongholdTabSMSG.Storage(
      learned = membership.skillLevel(SkillId.GUILD_STORAGE.name) > 0,
      used = used,
      slots = storages.slotsOf(membership),
      sealed = sealed,
      sendAwayCoins = config.storageSendAwayCoinsPerSlot * used,
    )
  }
}
