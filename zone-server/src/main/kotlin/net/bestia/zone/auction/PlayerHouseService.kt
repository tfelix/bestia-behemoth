package net.bestia.zone.auction

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.auction.persistence.AuctionListingRepository
import net.bestia.zone.auction.persistence.PlayerAuctionHouse
import net.bestia.zone.auction.persistence.PlayerAuctionHouseRepository
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.item.ecs.grantToLive
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.postal.parcel.ParcelService
import net.bestia.zone.prop.PlayerStructureDestroyedEvent
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.WorldObjectResidencyService
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.settlement.TownFellEvent
import net.bestia.zone.settlement.TownRegistry
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.skill.persistence.findByIdentifier
import net.bestia.zone.structure.StructureCollision
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.context.event.EventListener
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration

/**
 * What the owner of a player auction house does at it, and what happens to it when its town falls or it is
 * destroyed. See the design docs' economy page. Requests run off the tick; the events arrive on it and hand their
 * rows to a job.
 */
@Service
class PlayerHouseService(
  private val world: WorldView,
  private val connections: ConnectionInfoService,
  private val houses: AuctionHouses,
  private val playerHouses: PlayerHouses,
  private val houseRows: PlayerAuctionHouseRepository,
  private val listings: AuctionListingRepository,
  private val parcels: ParcelService,
  private val inventoryService: InventoryService,
  private val itemRepository: ItemRepository,
  private val commodityItems: CommodityItems,
  private val lootItems: LootItemEntitySpawner,
  private val structures: PlayerStructureRegistry,
  private val residency: WorldObjectResidencyService,
  private val collision: StructureCollision,
  private val towns: TownRegistry,
  private val worldService: WorldService,
  private val skills: SkillRepository,
  private val out: OutMessageProcessor,
  private val jobs: AsyncJobExecutor,
  private val transactions: TransactionTemplate,
  private val clock: Clock = Clock.systemUTC(),
) {

  private val tradePostOwnerSkillId: Long? by lazy { skills.findByIdentifier(SkillId.M_TRADE_POST_OWNER)?.id }

  /** Sends the window of [at], with the owner's part when [accountId] owns it. From the tick, so in a job. */
  fun open(accountId: AccountId, houseEntityId: EntityId, at: AuctionHouses.At.Player) {
    jobs.submit(at.house.structureId) { sendWindow(accountId, houseEntityId, at) }
  }

  fun setFee(accountId: AccountId, houseEntityId: EntityId, percent: Int) {
    val at = ownedHouseInReach(accountId, houseEntityId) ?: return
    val now = clock.instant()
    if (percent < AuctionFees.MIN_OWNER_FEE_PERCENT || percent > maxFeePercent(accountId)) {
      return refuse(accountId, OpError.REQUEST_REFUSED)
    }

    val refusal = transactions.execute {
      val row = houseRows.findLockedById(at.house.structureId) ?: return@execute OpError.AUCTION_LISTING_GONE
      val changeableAt = row.feeChangedAt?.plus(FEE_CHANGE_INTERVAL)
      if (changeableAt != null && changeableAt.isAfter(now)) return@execute OpError.AUCTION_FEE_CHANGED_RECENTLY

      row.feePercent = percent
      row.feeChangedAt = now
      playerHouses.remember(row)
      null
    }
    refusal?.let { return refuse(accountId, it) }

    sendWindow(accountId, houseEntityId, at.copy(house = playerHouses.of(at.house.structureId) ?: at.house))
  }

  /** In person: an unattended market does not bank its own profits. */
  fun collectTill(accountId: AccountId, houseEntityId: EntityId) {
    val at = ownedHouseInReach(accountId, houseEntityId) ?: return
    val coinId = commodityItems.coinItemId() ?: return refuse(accountId, OpError.REQUEST_REFUSED)
    val masterId = at.house.ownerMasterId

    val collected = transactions.execute {
      val row = houseRows.findLockedById(at.house.structureId) ?: return@execute 0L
      val amount = row.till.coerceAtMost(Int.MAX_VALUE.toLong())
      if (amount > 0) inventoryService.grantToMaster(masterId, coinId, amount.toInt())
      row.till -= amount
      amount
    } ?: 0L

    if (collected > 0) {
      world.grantToLive(connections.getSelectedMasterEntityId(accountId), listOf(ReservedItem.stack(coinId, collected.toInt(), 0)))
    }
    sendWindow(accountId, houseEntityId, at)
  }

  /** Only while no listing is up in it. The till goes home by post, the building comes down. */
  fun tearDown(accountId: AccountId, houseEntityId: EntityId) {
    val at = ownedHouseInReach(accountId, houseEntityId) ?: return
    val structureId = at.house.structureId
    if (listings.countByHouseStructureId(structureId) > 0) return refuse(accountId, OpError.AUCTION_HOUSE_NOT_EMPTY)

    transactions.execute {
      close(structureId, onTill = TillGoes.Post)
      houseRows.deleteById(structureId)
    }
    playerHouses.forget(structureId)

    world.post {
      val entry = structures.of(structureId) ?: return@post
      residency.remove(this, houseEntityId)
      val chunkSize = worldService.config.chunkSize.toLong()
      structures.remove(structureId, Math.floorDiv(entry.position.x, chunkSize).toInt(), Math.floorDiv(entry.position.y, chunkSize).toInt())
      collision.fall(structureId)
    }
    LOG.info { "Master ${at.house.ownerMasterId} tore down auction house $structureId" }
  }

  /** The house stays standing but closes: listings go home and the till is posted to the owner. */
  @EventListener
  fun onTownFell(event: TownFellEvent) {
    val town = towns.of(event.townId) ?: return
    val closing = structures.all()
      .filter { it.kind == StaticEntityKind.AUCTION_HOUSE && towns.metresBetween(town.sign, it.position) <= town.size.radiusMetres }
      .map { it.id }

    for (structureId in closing) {
      jobs.submit(structureId) { transactions.execute { close(structureId, onTill = TillGoes.Post) } }
    }
  }

  /** Raided or decayed: listings go home and the till spills where the house stood. */
  @EventListener
  fun onDestroyed(event: PlayerStructureDestroyedEvent) {
    if (event.kind != StaticEntityKind.AUCTION_HOUSE) return

    playerHouses.forget(event.structureId)
    jobs.submit(event.structureId) {
      transactions.execute {
        close(event.structureId, onTill = TillGoes.Spill(event.position))
        houseRows.deleteById(event.structureId)
      }
    }
  }

  private sealed interface TillGoes {
    data object Post : TillGoes
    data class Spill(val at: Vec3L) : TillGoes
  }

  /** Inside a transaction. */
  private fun close(structureId: Long, onTill: TillGoes) {
    for (listing in listings.findByHouseStructureId(structureId)) {
      parcels.postInstant(listing.sellerMasterId, AuctionService.SENDER_NAME) { parcel ->
        inventoryService.moveBetweenEscrows(listing.container, parcel)
      }
      listings.delete(listing)
    }

    val row = houseRows.findLockedById(structureId) ?: return
    val till = row.till.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    row.till = 0
    if (till <= 0) return
    val coinId = commodityItems.coinItemId() ?: return

    when (onTill) {
      TillGoes.Post -> {
        val coin = itemRepository.findByIdOrNull(coinId) ?: return
        parcels.postInstant(row.ownerMasterId, AuctionService.SENDER_NAME) { it.addStackable(coin, till) }
      }
      is TillGoes.Spill -> world.post { lootItems.spawnLootItem(this, coinId, till, onTill.at) }
    }
  }

  private fun sendWindow(accountId: AccountId, houseEntityId: EntityId, at: AuctionHouses.At.Player) {
    val owner = if (connections.getMasterId(accountId) == at.house.ownerMasterId) ownerView(accountId, at.house) else null

    out.sendToPlayer(
      accountId,
      AuctionHouseOpenSMSG(
        houseEntityId = houseEntityId,
        townName = at.townName,
        linkedTownNames = linkedTownNames(at.house),
        ownerName = at.house.ownerName,
        ownerFeePercent = at.house.feePercent,
        owner = owner,
      )
    )
  }

  private fun ownerView(accountId: AccountId, house: PlayerHouses.House): AuctionHouseOpenSMSG.OwnerView {
    val row: PlayerAuctionHouse? = houseRows.findByIdOrNull(house.structureId)

    return AuctionHouseOpenSMSG.OwnerView(
      maxFeePercent = maxFeePercent(accountId),
      till = row?.till ?: 0,
      feeChangeableAt = house.feeChangedAt?.plus(FEE_CHANGE_INTERVAL)?.takeIf { it.isAfter(clock.instant()) },
      canTearDown = listings.countByHouseStructureId(house.structureId) == 0L,
    )
  }

  /** The towns of every house this one shares listings with, itself included. */
  private fun linkedTownNames(house: PlayerHouses.House): List<String> {
    return world.read {
      playerHouses.sharingWith(house)
        .mapNotNull { structures.of(it)?.position }
        .mapNotNull { towns.standingAt(it)?.name }
        .distinct()
    }
  }

  private fun maxFeePercent(accountId: AccountId): Int {
    val masterEntityId = connections.getSelectedMasterEntityId(accountId)
    val level = tradePostOwnerSkillId?.let { skillId -> world.read { get(masterEntityId, KnownSkills::class)?.levelOf(skillId) } } ?: 0

    return AuctionFees.maxOwnerFeePercent(level.coerceAtLeast(1))
  }

  private fun ownedHouseInReach(accountId: AccountId, houseEntityId: EntityId): AuctionHouses.At.Player? {
    val actorId = connections.getActiveEntityId(accountId)
    val at = world.read { houses.houseInReach(this, actorId, houseEntityId) } as? AuctionHouses.At.Player
    if (at == null) {
      refuse(accountId, OpError.AUCTION_OUT_OF_RANGE)
      return null
    }
    if (at.house.ownerMasterId != connections.getMasterId(accountId)) {
      refuse(accountId, OpError.REQUEST_REFUSED)
      return null
    }

    return at
  }

  private fun refuse(accountId: AccountId, code: OpError) {
    out.sendToPlayer(accountId, OperationErrorSMSG(code))
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    /** The docs' once a day. */
    val FEE_CHANGE_INTERVAL: Duration = Duration.ofHours(24)
  }
}
