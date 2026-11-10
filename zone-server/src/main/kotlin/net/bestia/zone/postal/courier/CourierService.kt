package net.bestia.zone.postal.courier

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.persistence.StatusEffectPersistenceService
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.WorldReserve
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.GameMetrics.CoinSource
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.ItemProvenance
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.item.ecs.SpilledProperty
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.grantToLive
import net.bestia.zone.item.enchant.ActiveItemEnchantment
import net.bestia.zone.item.enchant.ItemEnchantmentId
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.item.persistence.ItemContainer
import net.bestia.zone.item.persistence.ItemInstanceRepository
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.item.persistence.findByIdentifierOrThrow
import net.bestia.zone.postal.office.PostOffices
import net.bestia.zone.postal.parcel.Freight
import net.bestia.zone.postal.parcel.ParcelService
import net.bestia.zone.postal.parcel.ParcelService.Outcome
import net.bestia.zone.postal.persistence.Parcel
import net.bestia.zone.postal.persistence.ParcelRepository
import net.bestia.zone.postal.seal.PostalSeal
import net.bestia.zone.util.EntityId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant

/**
 * Player couriers: the route board, the sealed mail sack and its delivery. See "Carrying the Mail" on the postal
 * page of the design docs. Off the tick, like [net.bestia.zone.postal.parcel.ParcelService].
 */
@Service
class CourierService(
  private val parcels: ParcelRepository,
  private val masters: MasterRepository,
  private val offices: PostOffices,
  private val inventoryService: InventoryService,
  private val itemRepository: ItemRepository,
  private val itemInstances: ItemInstanceRepository,
  private val lootItems: LootItemEntitySpawner,
  private val commodityItems: CommodityItems,
  private val reserve: WorldReserve,
  private val parcelService: ParcelService,
  private val statusEffects: StatusEffectService,
  private val storedEffects: StatusEffectPersistenceService,
  private val world: WorldView,
  private val transactions: TransactionTemplate,
  private val clock: Clock = Clock.systemUTC(),
) {

  /** A master at a route board, with what its bag can still take. */
  data class Courier(val masterId: Long, val liveEntityId: EntityId, val freeWeight: Int)

  data class BoardLine(
    val parcelId: Long,
    val destinationName: String,
    val weightUnits: Int,
    val pay: Long,
    val latestArrival: Instant,
  )

  /** What a courier was paid for the parcels it brought. */
  data class Delivery(val parcels: Int, val pay: Long)

  @Transactional(readOnly = true)
  fun routeBoard(at: PostOffices.Office): List<BoardLine> {
    val now = clock.instant()

    return parcels.findByOriginOfficeAndSackInstanceIdIsNullOrderByLatestArrival(at.settlement)
      .filter { it.isOnBoard(now) }
      .map { BoardLine(it.id, officeName(it.destinationOffice), it.weightUnits, Freight.courierPay(it.freightFee), it.latestArrival) }
  }

  /** Puts [parcelIds], all bound for one office, into a new sealed sack. @return why not, or null once it is in the bag */
  fun take(courier: Courier, at: PostOffices.Office, parcelIds: List<Long>): Outcome.Refused? {
    val now = clock.instant()

    val taken = transactions.execute {
      val wanted = parcelIds.distinct().map { parcels.findLockedById(it) ?: return@execute HAND_MADE }
      // The board only shows parcels that are on it, so anything else is a hand-made packet.
      if (wanted.isEmpty() || wanted.any { !it.isOnBoard(now) || it.originOffice != at.settlement }) return@execute HAND_MADE
      if (wanted.map { it.destinationOffice }.distinct().size != 1) return@execute HAND_MADE

      val weight = wanted.sumOf { it.weightUnits }
      if (weight > courier.freeWeight) return@execute Outcome.Refused(OpError.ROUTE_TOO_HEAVY)

      Sacked(sealSack(courier.masterId, wanted, weight))
    }

    val sack = (taken as? Sacked)?.sack ?: return taken as? Outcome.Refused ?: HAND_MADE
    world.grantToLive(courier.liveEntityId, listOf(sack))

    return null
  }

  /**
   * Hands over every sack [courier] carries to [at], before the deadline, and pays the courier its share.
   * @return null when it carries nothing for here
   */
  fun deliver(courier: Courier, at: PostOffices.Office): Delivery? {
    val now = clock.instant()
    val coinId = commodityItems.coinItemId() ?: return null

    val delivered = transactions.execute {
      val carried = parcels.findByCourierMasterIdAndDestinationOffice(courier.masterId, at.settlement)
        .filter { it.sackInstanceId != null && !it.hasArrived(now) }
      if (carried.isEmpty()) return@execute null

      val sacks = carried.mapNotNull { it.sackInstanceId }.distinct()
      sacks.forEach { inventoryService.destroyInstance(courier.masterId, it) }
      val pay = carried.sumOf { Freight.courierPay(it.freightFee) }
      carried.forEach { it.deliver(now) }
      if (pay > 0) inventoryService.grantToMaster(courier.masterId, coinId, pay.toInt())

      Handed(Delivery(carried.size, pay), sacks)
    } ?: return null

    // Past the commit, like every move between the database and a live bag.
    removeFromLiveBag(courier.liveEntityId, delivered.sacks)
    world.grantToLive(courier.liveEntityId, listOf(ReservedItem.stack(coinId, delivered.delivery.pay.toInt(), weight = 0)))
    world.read { reserve.charge(delivered.delivery.pay.toDouble(), CoinSource.COURIER) }

    return delivered.delivery
  }

  /**
   * A courier who missed the deadline is paid nothing and struck off; the seal already sent the parcels on, as
   * their latest arrival has passed. This happens whether the courier is in the world or not.
   */
  @Scheduled(fixedDelay = 60_000L)
  fun strikeOffLate() {
    val now = clock.instant()

    val late = transactions.execute {
      parcels.findByCourierMasterIdIsNotNullAndLatestArrivalLessThanEqual(now)
        .groupBy { it.courierMasterId!! }
        .map { (courierMasterId, carried) ->
          val sacks = carried.mapNotNull { it.sackInstanceId }.distinct()
          sacks.forEach { inventoryService.destroyInstance(courierMasterId, it) }
          carried.forEach { it.sackInstanceId = null; it.courierMasterId = null }
          LateCourier(masters.findByIdOrThrow(courierMasterId).entityId, sacks)
        }
    }.orEmpty()

    late.forEach(::strikeOff)
  }

  /**
   * The sacks of a killed courier fall where it died, still sealed, and the seal starts its return. Nobody carries
   * their parcels until somebody picks a sack up.
   */
  fun dropSacks(courierMasterId: Long, sacks: List<Long>, fellAt: Vec3L) {
    val now = clock.instant()

    val fallen = transactions.execute {
      val sackItemId = itemRepository.findByIdentifierOrThrow(MAIL_SACK).id
      sacks.mapNotNull { sackId ->
        val removed = inventoryService.removeNamedFromOwner(courierMasterId, null, sackItemId, 1, sackId, null)
        val sack = removed?.instance ?: return@mapNotNull null
        parcels.findBySackInstanceId(sackId).forEach { it.courierMasterId = null }
        FallenSack(sackItemId, sackId, sack.enchantments(now), sack.heldWeight)
      }
    }.orEmpty()

    world.read {
      fallen.forEach { sack ->
        lootItems.spawnLootItem(
          this, itemId = sack.itemId, amount = 1, pos = fellAt, uniqueId = sack.sackId, enchantments = sack.seal,
          heldWeight = sack.heldWeight,
        )
      }
    }
  }

  /** Whoever picked up a fallen sack carries it now, with the same deadline and the full pay. */
  @Transactional
  fun takeOver(sackId: Long, courierMasterId: Long) {
    parcels.findBySackInstanceId(sackId).forEach { it.courierMasterId = courierMasterId }
  }

  /**
   * A Dispel broke the seal of a fallen sack. Its goods spill around [at] as plain ground items, and taking them is
   * stealing. The goods are lost to the senders, but each gets the freight fee back, since no courier earned it.
   */
  fun spill(sackId: Long, at: Vec3L) {
    val coinId = commodityItems.coinItemId() ?: return

    val spilled = transactions.execute {
      val coin = itemRepository.findByIdOrNull(coinId) ?: return@execute null
      val robbed = parcels.findBySackInstanceId(sackId)
      val goods = robbed.flatMap { parcel -> parcel.container.removeAllFree().map(::spilledGoods) }

      robbed.forEach { parcel ->
        parcelService.postInstant(parcel.senderMasterId, POST) { it.addStackable(coin, parcel.freightFee.toInt()) }
        parcels.delete(parcel)
      }
      itemInstances.deleteById(sackId)

      Spill(goods, refunds = robbed.sumOf { it.freightFee })
    } ?: return

    world.read {
      spilled.goods.forEach { goods ->
        val lying = lootItems.spawnLootItem(
          this, itemId = goods.itemId, amount = goods.amount, pos = at, uniqueId = goods.uniqueId,
          provenance = goods.provenance,
        )
        add(lying, SpilledProperty(goods.itemLevel))
      }
      reserve.charge(spilled.refunds.toDouble(), CoinSource.FREIGHT)
    }
  }

  private data class Spill(val goods: List<SpilledGoods>, val refunds: Long)

  private data class SpilledGoods(
    val itemId: Long,
    val amount: Int,
    val uniqueId: Long,
    val provenance: ItemProvenance?,
    val itemLevel: Int,
  )

  private fun spilledGoods(detached: ItemContainer.DetachedItem): SpilledGoods {
    val instance = detached.instance

    return SpilledGoods(
      itemId = detached.template.id,
      amount = detached.amount,
      uniqueId = instance?.id ?: 0L,
      provenance = instance?.provenance ?: detached.provenance,
      itemLevel = instance?.itemLevel ?: detached.template.baseLevel,
    )
  }

  /** A fallen sack nobody picked up: its parcels wait at their origin again, due at the same latest arrival. */
  @Transactional
  fun returnToBoard(sackId: Long) {
    parcels.findBySackInstanceId(sackId).forEach { parcel ->
      parcel.sackInstanceId = null
      parcel.courierMasterId = null
    }
    itemInstances.deleteById(sackId)
  }

  private data class FallenSack(
    val itemId: Long,
    val sackId: Long,
    val seal: List<ActiveItemEnchantment>,
    val heldWeight: Int?,
  )

  private data class LateCourier(val masterEntityId: EntityId, val sacks: List<Long>)

  private fun strikeOff(late: LateCourier) {
    removeFromLiveBag(late.masterEntityId, late.sacks)
    val inWorld = world.modify(late.masterEntityId) { id ->
      if (!has(id, StatusEffects::class)) return@modify false
      statusEffects.applyEffect(this, id, StatusEffectId.STRUCK_OFF, level = 1)
    } == true

    if (!inWorld) {
      storedEffects.seed(late.masterEntityId, StatusEffectId.STRUCK_OFF)
    }
  }

  private data class Sacked(val sack: ReservedItem)

  private data class Handed(val delivery: Delivery, val sacks: List<Long>)

  private fun removeFromLiveBag(entityId: EntityId, sacks: List<Long>) {
    world.modify(entityId) { id ->
      val bag = get(id, Inventory::class) ?: return@modify
      sacks.forEach { bag.removeByUniqueId(it) }
    }
  }

  private fun sealSack(courierMasterId: Long, wanted: List<Parcel>, weight: Int): ReservedItem {
    val template = itemRepository.findByIdentifierOrThrow(MAIL_SACK)
    val sack = inventoryService.mintInstanceForMaster(courierMasterId, template)
    sack.heldWeight = weight
    sack.enchant(ActiveItemEnchantment(ItemEnchantmentId.POSTAL_SEAL, PostalSeal.STRENGTH))

    wanted.forEach { parcel ->
      parcel.sackInstanceId = sack.id
      parcel.courierMasterId = courierMasterId
    }

    return ReservedItem(
      offerSlotId = 0L, itemId = template.id, amount = 1, weight = weight, uniqueId = sack.id, stackable = false,
      durability = 0, maxDurability = 0, slots = 0, upgradeLevel = 0, itemLevel = template.baseLevel,
      rolledStats = emptyList(),
    )
  }

  private fun officeName(settlement: Int): String {
    return world.read { offices.officeOf(settlement)?.name }.orEmpty()
  }

  companion object {
    const val MAIL_SACK = "mail_sack"

    /** Who a refund comes from, as the auction house signs its own mail. */
    const val POST = "Postal Service"

    private val HAND_MADE = Outcome.Refused(OpError.REQUEST_REFUSED)
  }
}
