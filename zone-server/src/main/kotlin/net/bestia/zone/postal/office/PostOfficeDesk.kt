package net.bestia.zone.postal.office

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.bnet.proto.ParcelProto
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.item.ecs.CarryCapacity
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.postal.courier.CourierService
import net.bestia.zone.postal.parcel.ParcelService
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.townsfolk.conversation.ServiceCounter
import net.bestia.zone.townsfolk.conversation.Speaker
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * The post office counter. A postmaster at work opens the post office window instead of a conversation, as a
 * merchant at work opens the shop, and so does clicking a player-built office. Called on the tick; the master's
 * row is read and written in a job.
 */
@Service
class PostOfficeDesk(
  private val offices: PostOffices,
  private val postmasters: PostmasterCounters,
  private val masters: MasterRepository,
  private val connections: ConnectionInfoService,
  private val jobs: AsyncJobExecutor,
  private val transactions: TransactionTemplate,
  private val out: OutMessageProcessor,
  private val parcels: ParcelService,
  private val world: WorldView,
  private val itemTemplates: ItemTemplateRegistry,
  private val couriers: CourierService,
) : ServiceCounter {

  /** One line of a parcel being weighed or sent: what the bag shows, not what the database holds. */
  data class Line(val playerItemId: Long, val amount: Int) {
    companion object {
      fun allOf(items: List<ParcelProto.ParcelItem>): List<Line> {
        return items.map { Line(it.playerItemId, it.amount) }
      }
    }
  }

  /** @return true when the window is on its way, false when this is not a postmaster at work. */
  override fun serve(accountId: AccountId, speaker: Speaker): Boolean {
    val counter = postmasters.counterOf(speaker)?.takeIf { it.office != null } ?: return false

    return open(accountId, counter)
  }

  /** @return false when [counter] is closed. */
  fun open(accountId: AccountId, counter: PostCounter): Boolean {
    val office = counter.office ?: return false
    val masterId = connections.getMasterId(accountId)

    jobs.submit(masterId) { answer(accountId, masterId, counter, office, newHome = null) }

    return true
  }

  fun setHome(accountId: AccountId, counter: PostCounter) {
    val office = counter.office ?: return
    val masterId = connections.getMasterId(accountId)

    jobs.submit(masterId) { answer(accountId, masterId, counter, office, newHome = office.settlement) }
  }

  fun quote(accountId: AccountId, counter: PostCounter, recipientName: String, lines: List<Line>) {
    val office = counter.office ?: return closed(accountId)
    val goods = goodsOf(accountId, lines) ?: return refuse(accountId, OpError.REQUEST_REFUSED)
    if (goods.any { itemTemplates.isBound(it.itemId) }) return refuse(accountId, OpError.ITEM_BOUND)

    jobs.submit(connections.getMasterId(accountId)) {
      when (val outcome = parcels.quote(office, recipientName, goods)) {
        is ParcelService.Outcome.Quoted -> out.sendToPlayer(accountId, ParcelQuoteSMSG(outcome.quote))
        is ParcelService.Outcome.Refused -> refuse(accountId, outcome.code, outcome.args)
      }
    }
  }

  fun send(accountId: AccountId, counter: PostCounter, recipientName: String, lines: List<Line>, coachOnly: Boolean) {
    val office = counter.office ?: return closed(accountId)
    val goods = goodsOf(accountId, lines) ?: return refuse(accountId, OpError.REQUEST_REFUSED)
    if (goods.any { itemTemplates.isBound(it.itemId) }) return refuse(accountId, OpError.ITEM_BOUND)
    val customer = customerOf(accountId)

    jobs.submit(customer.masterId) {
      when (val outcome = parcels.send(customer, office, recipientName, goods, coachOnly)) {
        is ParcelService.Outcome.Quoted -> out.sendToPlayer(accountId, ParcelSentSMSG(outcome.quote))
        is ParcelService.Outcome.Refused -> refuse(accountId, outcome.code, outcome.args)
      }
    }
  }

  fun collect(accountId: AccountId, counter: PostCounter, parcelId: Long) {
    val office = counter.office ?: return closed(accountId)
    val customer = customerOf(accountId)

    jobs.submit(customer.masterId) {
      parcels.collect(customer, office, parcelId)?.let { refuse(accountId, it.code, it.args) }
      showParcels(accountId, customer.masterId, counter, office)
    }
  }

  fun refuseParcel(accountId: AccountId, counter: PostCounter, parcelId: Long) {
    val office = counter.office ?: return closed(accountId)
    val customer = customerOf(accountId)

    jobs.submit(customer.masterId) {
      parcels.refuse(customer, parcelId)?.let { refuse(accountId, it.code, it.args) }
      showParcels(accountId, customer.masterId, counter, office)
    }
  }

  /** On the tick, which knows what the courier's bag can still take. */
  fun takeParcels(world: World, accountId: AccountId, counter: PostCounter, parcelIds: List<Long>) {
    val office = counter.office ?: return closed(accountId)
    val entityId = connections.getSelectedMasterEntityId(accountId)
    if (world.get(entityId, StatusEffects::class)?.hasEffect(StatusEffectId.STRUCK_OFF.id) == true) {
      return refuse(accountId, OpError.COURIER_STRUCK_OFF)
    }
    val bag = world.get(entityId, Inventory::class) ?: return
    val capacity = world.get(entityId, CarryCapacity::class) ?: return
    val courier = CourierService.Courier(connections.getMasterId(accountId), entityId, capacity.max - bag.totalWeight)

    jobs.submit(courier.masterId) {
      couriers.take(courier, office, parcelIds)?.let { refuse(accountId, it.code, it.args) }
      showRouteBoard(accountId, counter, office)
    }
  }

  /** What [lines] name in the live bag, worn gear left out. Null when any of it is not there. */
  private fun goodsOf(accountId: AccountId, lines: List<Line>): List<ParcelService.Goods>? {
    val entityId = connections.getSelectedMasterEntityId(accountId)

    return world.read {
      val bag = get(entityId, Inventory::class) ?: return@read null
      val worn = get(entityId, Equipment::class)
      lines.map { line ->
        val held = bag.byPlayerItemId(line.playerItemId)
          ?.takeUnless { it.equipped || worn?.isWorn(it.uniqueId) == true || it.amount < line.amount }
          ?: return@read null
        ParcelService.Goods(held.itemId, held.uniqueId, line.amount, held.weight, held.provenance)
      }
    }
  }

  private fun customerOf(accountId: AccountId): ParcelService.Customer {
    return ParcelService.Customer(connections.getMasterId(accountId), connections.getSelectedMasterEntityId(accountId))
  }

  private fun showParcels(accountId: AccountId, masterId: Long, counter: PostCounter, office: PostOffices.Office) {
    out.sendToPlayer(accountId, ParcelsSMSG(counter.entityId, parcels.parcelsOf(masterId, office)))
  }

  private fun showRouteBoard(
    accountId: AccountId,
    counter: PostCounter,
    office: PostOffices.Office,
    delivered: CourierService.Delivery? = null,
  ) {
    out.sendToPlayer(accountId, RouteBoardSMSG(counter.entityId, couriers.routeBoard(office), delivered))
  }

  private fun closed(accountId: AccountId) {
    refuse(accountId, OpError.POST_OFFICE_CLOSED)
  }

  private fun refuse(accountId: AccountId, code: OpError, args: List<String> = emptyList()) {
    out.sendToPlayer(accountId, OperationErrorSMSG(code, args))
  }

  private fun answer(
    accountId: AccountId,
    masterId: Long,
    counter: PostCounter,
    office: PostOffices.Office,
    newHome: Int?,
  ) {
    val home = transactions.execute {
      val master = masters.findByIdOrThrow(masterId)
      newHome?.let { master.homePostOffice = it }
      offices.homeOf(master.homePostOffice, master.homeSettlementName)
    }
    val homeName = home?.let { offices.officeOf(it)?.name }.orEmpty()

    out.sendToPlayer(
      accountId,
      PostOfficeOpenSMSG(counter.entityId, office.name, office.level, homeName, isHome = home == office.settlement)
    )
    val courier = CourierService.Courier(masterId, connections.getSelectedMasterEntityId(accountId), freeWeight = 0)
    val delivered = couriers.deliver(courier, office)
    showParcels(accountId, masterId, counter, office)
    showRouteBoard(accountId, counter, office, delivered)
  }
}
