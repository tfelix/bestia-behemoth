package net.bestia.zone.postal.parcel

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.WorldReserve
import net.bestia.zone.entity.GameMetrics.CoinSource
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.item.ecs.grantToLive
import net.bestia.zone.item.ecs.takeFromLive
import net.bestia.zone.item.persistence.ItemContainer
import net.bestia.zone.item.ItemProvenance
import net.bestia.zone.postal.office.PostOffices
import net.bestia.zone.postal.persistence.Parcel
import net.bestia.zone.postal.persistence.ParcelRepository
import net.bestia.zone.util.EntityId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Parcels: sent at a post office, carried by the coach, collected at the recipient's home office. See the postal
 * page of the design docs. Off the tick: every call reads or writes the database, and the world is only borrowed
 * for the offices, the road and the live bags.
 */
@Service
class ParcelService(
  private val parcels: ParcelRepository,
  private val masters: MasterRepository,
  private val offices: PostOffices,
  private val roads: RoadDistance,
  private val inventoryService: InventoryService,
  private val commodityItems: CommodityItems,
  private val world: WorldView,
  private val reserve: WorldReserve,
  private val events: ApplicationEventPublisher,
  private val transactions: TransactionTemplate,
  private val clock: Clock = Clock.systemUTC(),
) {

  /** A master at a post office, and its entity, so goods and coins leave or reach its live bag too. */
  data class Customer(val masterId: Long, val liveEntityId: EntityId)

  /** One pile or copy the customer hands over, as its live bag holds it. */
  data class Goods(
    val itemId: Long,
    val uniqueId: Long,
    val amount: Int,
    val weight: Int,
    val provenance: ItemProvenance?,
  )

  data class Quote(
    val recipientName: String,
    val destinationName: String,
    val weightUnits: Int,
    val fee: Long,
    val latestArrival: Instant,
  )

  data class ParcelView(
    val parcelId: Long,
    val senderName: String,
    val destinationName: String,
    val latestArrival: Instant,
    val arrived: Boolean,
    val collectableHere: Boolean,
    val returning: Boolean,
    val goods: List<ReservedItem>,
  )

  sealed interface Outcome {
    data class Quoted(val quote: Quote) : Outcome
    data class Refused(val code: OpError, val args: List<String> = emptyList()) : Outcome
  }

  fun quote(origin: PostOffices.Office, recipientName: String, goods: List<Goods>): Outcome {
    val recipient = masters.findByName(recipientName.trim())
      ?: return Outcome.Refused(OpError.MAIL_RECIPIENT_UNKNOWN, listOf(recipientName.trim()))
    val destination = offices.homeOf(recipient.homePostOffice, recipient.homeSettlementName)
      ?: return Outcome.Refused(OpError.REQUEST_REFUSED)

    return quoteTo(origin, recipient.name, destination, goods.sumOf { it.weight * it.amount })
  }

  fun send(
    sender: Customer,
    origin: PostOffices.Office,
    recipientName: String,
    goods: List<Goods>,
    coachOnly: Boolean,
  ): Outcome {
    if (goods.isEmpty() || goods.any { it.amount <= 0 }) return Outcome.Refused(OpError.REQUEST_REFUSED)
    val coinId = commodityItems.coinItemId() ?: return Outcome.Refused(OpError.REQUEST_REFUSED)
    if (goods.any { it.itemId == coinId }) return Outcome.Refused(OpError.REQUEST_REFUSED)

    val quoted = quote(origin, recipientName, goods)
    if (quoted !is Outcome.Quoted) return quoted
    val quote = quoted.quote
    val recipient = masters.findByName(quote.recipientName) ?: return Outcome.Refused(OpError.REQUEST_REFUSED)
    val destination = offices.homeOf(recipient.homePostOffice, recipient.homeSettlementName)
      ?: return Outcome.Refused(OpError.REQUEST_REFUSED)

    val escrowed = transactions.execute { status ->
      escrow(sender.masterId, goods, quote, recipient.id, origin, destination, coachOnly, coinId)
        .also { if (it is Escrow.Refused) status.setRollbackOnly() }
    }
    val moved = (escrowed as? Escrow.Moved)?.goods ?: return (escrowed as? Escrow.Refused)?.refusal
      ?: Outcome.Refused(OpError.REQUEST_REFUSED)

    // Past the commit. A live bag that disagrees is corrected by the next login; the database decided.
    moved.forEach { world.takeFromLive(sender.liveEntityId, it) }
    world.takeFromLive(sender.liveEntityId, ReservedItem.stack(coinId, quote.fee.toInt(), weight = 0))
    world.read { reserve.charge(-quote.fee.toDouble(), CoinSource.FREIGHT) }

    return quoted
  }

  /**
   * Every parcel to [masterId], whether on the road or waiting; [at] decides which may be collected there, and
   * none may when the master is at no post office.
   */
  @Transactional(readOnly = true)
  fun parcelsOf(masterId: Long, at: PostOffices.Office?): List<ParcelView> {
    val now = clock.instant()

    return parcels.findByRecipientMasterIdOrderByLatestArrival(masterId).map { parcel ->
      ParcelView(
        parcelId = parcel.id,
        senderName = parcel.senderName,
        destinationName = officeName(parcel.destinationOffice),
        latestArrival = parcel.latestArrival,
        arrived = parcel.hasArrived(now),
        collectableHere = at != null && parcel.hasArrived(now) && mayCollectAt(at, parcel),
        returning = parcel.returning,
        goods = parcel.container.slots.map { ReservedItem.of(it, it.itemInstance?.itemLevel ?: 0) },
      )
    }
  }

  /** @return why not, or null once the goods are in the bag */
  fun collect(customer: Customer, at: PostOffices.Office, parcelId: Long): Outcome.Refused? {
    val now = clock.instant()

    val collected = transactions.execute {
      val parcel = parcels.findLockedById(parcelId)?.takeIf { it.recipientMasterId == customer.masterId }
        ?: return@execute Escrow.Refused(Outcome.Refused(OpError.REQUEST_REFUSED))
      if (!parcel.hasArrived(now)) return@execute Escrow.Refused(Outcome.Refused(OpError.PARCEL_NOT_ARRIVED))
      if (!mayCollectAt(at, parcel)) {
        val waitsAt = officeName(parcel.destinationOffice)
        return@execute Escrow.Refused(Outcome.Refused(OpError.PARCEL_NOT_HERE, listOf(waitsAt)))
      }

      val goods = inventoryService.moveFromEscrow(parcel.container, customer.masterId)
      parcels.delete(parcel)
      Escrow.Moved(goods)
    }

    if (collected !is Escrow.Moved) {
      return (collected as? Escrow.Refused)?.refusal ?: Outcome.Refused(OpError.REQUEST_REFUSED)
    }
    world.grantToLive(customer.liveEntityId, collected.goods)

    return null
  }

  /** The parcel goes back to its sender at no cost, to the sender's home office. */
  fun refuse(customer: Customer, parcelId: Long): Outcome.Refused? {
    return transactions.execute {
      val parcel = parcels.findLockedById(parcelId)
        ?.takeIf { it.recipientMasterId == customer.masterId && !it.returning }
        ?: return@execute Outcome.Refused(OpError.REQUEST_REFUSED)
      sendBack(parcel)
      null
    }
  }

  /**
   * Mail that needs no coach: what an auction house owes a master. [fill] puts the goods into the parcel. Call it
   * inside the transaction that took them from where they were.
   */
  fun postInstant(recipientMasterId: Long, senderName: String, fill: (ItemContainer) -> Unit) {
    val recipient = masters.findByIdOrThrow(recipientMasterId)
    val home = offices.homeOf(recipient.homePostOffice, recipient.homeSettlementName) ?: 0
    val now = clock.instant()

    val parcel = Parcel(
      senderMasterId = 0,
      senderName = senderName,
      recipientMasterId = recipientMasterId,
      originOffice = home,
      destinationOffice = home,
      weightUnits = 0,
      freightFee = 0,
      coachOnly = true,
      sentAt = now,
      latestArrival = now,
    )
    parcel.collectAnywhere = true
    fill(parcel.container)
    parcels.save(parcel)
  }

  /** Tells each recipient once that a parcel waits for them. */
  @Scheduled(fixedDelay = 60_000L)
  fun announceArrivals() {
    val arrived = transactions.execute {
      parcels.findByAnnouncedFalseAndLatestArrivalLessThanEqual(clock.instant()).map { parcel ->
        parcel.announced = true
        val recipient = masters.findByIdOrThrow(parcel.recipientMasterId)
        val office = officeName(parcel.destinationOffice)
        ParcelArrivedEvent(recipient.account.id, recipient.id, parcel.id, parcel.senderName, office)
      }
    }.orEmpty()

    arrived.forEach(events::publishEvent)
  }

  /** A parcel nobody collects for [KEPT_FOR] goes back to its sender. What the sender leaves too is kept for now. */
  @Scheduled(fixedDelay = 60 * 60 * 1000L)
  fun returnUncollected() {
    transactions.execute {
      parcels.findByReturningFalseAndLatestArrivalLessThan(clock.instant().minus(KEPT_FOR)).forEach(::sendBack)
    }
  }

  private fun quoteTo(origin: PostOffices.Office, recipientName: String, destination: Int, weightUnits: Int): Outcome {
    if (weightUnits > Freight.MAX_WEIGHT_UNITS) return Outcome.Refused(OpError.PARCEL_TOO_HEAVY)

    val km = roads.km(origin.settlement, destination) ?: return Outcome.Refused(OpError.REQUEST_REFUSED)
    val now = clock.instant()
    // A parcel to the office it is handed in at needs no coach.
    val latestArrival = if (origin.settlement == destination) now else CoachTimetable.latestArrival(origin.level, km, now)
    val fee = Freight.fee(km, weightUnits)

    return Outcome.Quoted(Quote(recipientName, officeName(destination), weightUnits, fee, latestArrival))
  }

  /** Goods moving between a bag and a parcel, in one transaction, or why they did not. */
  private sealed interface Escrow {
    data class Moved(val goods: List<ReservedItem>) : Escrow
    data class Refused(val refusal: Outcome.Refused) : Escrow
  }

  private fun escrow(
    senderId: Long, goods: List<Goods>, quote: Quote, recipientId: Long, origin: PostOffices.Office, destination: Int,
    coachOnly: Boolean, coinId: Long,
  ): Escrow {
    if (!inventoryService.consumeAll(senderId, listOf(coinId to quote.fee.toInt()))) {
      return Escrow.Refused(Outcome.Refused(OpError.PARCEL_CANNOT_AFFORD))
    }

    val parcel = Parcel(
      senderMasterId = senderId,
      senderName = masters.findByIdOrThrow(senderId).name,
      recipientMasterId = recipientId,
      originOffice = origin.settlement,
      destinationOffice = destination,
      weightUnits = quote.weightUnits,
      freightFee = quote.fee,
      coachOnly = coachOnly,
      sentAt = clock.instant(),
      latestArrival = quote.latestArrival,
    )
    // A client only offers what its bag holds, so goods that are not there are a hand-made packet.
    val moved = goods.map {
      inventoryService.moveToEscrow(senderId, it.itemId, it.uniqueId, it.amount, parcel.container, it.provenance)
        ?: return Escrow.Refused(Outcome.Refused(OpError.REQUEST_REFUSED))
    }
    parcels.save(parcel)

    return Escrow.Moved(moved)
  }

  private fun sendBack(parcel: Parcel) {
    val sender = masters.findByIdOrThrow(parcel.senderMasterId)
    val home = offices.homeOf(sender.homePostOffice, sender.homeSettlementName) ?: parcel.originOffice
    val km = roads.km(parcel.destinationOffice, home) ?: 0.0
    val level = world.read { offices.officeOf(parcel.destinationOffice)?.level } ?: 2

    parcel.recipientMasterId = sender.id
    parcel.originOffice = parcel.destinationOffice
    parcel.destinationOffice = home
    parcel.latestArrival = CoachTimetable.latestArrival(level, km, clock.instant())
    parcel.returning = true
    parcel.announced = false
  }

  private fun mayCollectAt(at: PostOffices.Office, parcel: Parcel): Boolean {
    return parcel.collectAnywhere || isNear(at.settlement, parcel.destinationOffice)
  }

  private fun isNear(office: Int, destination: Int): Boolean {
    if (office == destination) return true
    val km = world.read { offices.kmBetween(office, destination) } ?: return false
    return km <= COLLECT_RANGE_KM
  }

  private fun officeName(settlement: Int): String {
    return world.read { offices.officeOf(settlement)?.name }.orEmpty()
  }

  companion object {
    /** How long a parcel waits at its office before it goes back. */
    val KEPT_FOR: Duration = Duration.ofDays(30)

    /** A master collects at the home office or at any office this close to it. */
    const val COLLECT_RANGE_KM = 10.0
  }
}
