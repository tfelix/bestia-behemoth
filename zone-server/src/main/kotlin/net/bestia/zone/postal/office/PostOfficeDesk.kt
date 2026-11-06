package net.bestia.zone.postal.office

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.bnet.proto.ParcelProto
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
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
import net.bestia.zone.world.time.BestiaClock
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * The postmaster's counter. A postmaster at work opens the post office window instead of a conversation, as a
 * merchant at work opens the shop. Called on the tick; the master's row is read and written in a job.
 */
@Service
class PostOfficeDesk(
  private val offices: PostOffices,
  private val clock: BestiaClock,
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
    val office = officeAtWork(speaker) ?: return false
    val masterId = connections.getMasterId(accountId)

    jobs.submit(masterId) { answer(accountId, masterId, speaker, office, newHome = null) }

    return true
  }

  fun setHome(accountId: AccountId, postmaster: Speaker) {
    val office = officeAtWork(postmaster) ?: return
    val masterId = connections.getMasterId(accountId)

    jobs.submit(masterId) { answer(accountId, masterId, postmaster, office, newHome = office.settlement) }
  }

  fun quote(accountId: AccountId, postmaster: Speaker, recipientName: String, lines: List<Line>) {
    val office = officeAtWork(postmaster) ?: return closed(accountId)
    val goods = goodsOf(accountId, lines) ?: return refuse(accountId, OpError.REQUEST_REFUSED)
    if (goods.any { itemTemplates.isBound(it.itemId) }) return refuse(accountId, OpError.ITEM_BOUND)

    jobs.submit(connections.getMasterId(accountId)) {
      when (val outcome = parcels.quote(office, recipientName, goods)) {
        is ParcelService.Outcome.Quoted -> out.sendToPlayer(accountId, ParcelQuoteSMSG(outcome.quote))
        is ParcelService.Outcome.Refused -> refuse(accountId, outcome.code, outcome.args)
      }
    }
  }

  fun send(accountId: AccountId, postmaster: Speaker, recipientName: String, lines: List<Line>, coachOnly: Boolean) {
    val office = officeAtWork(postmaster) ?: return closed(accountId)
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

  fun collect(accountId: AccountId, postmaster: Speaker, parcelId: Long) {
    val office = officeAtWork(postmaster) ?: return closed(accountId)
    val customer = customerOf(accountId)

    jobs.submit(customer.masterId) {
      parcels.collect(customer, office, parcelId)?.let { refuse(accountId, it.code, it.args) }
      showParcels(accountId, customer.masterId, postmaster, office)
    }
  }

  fun refuseParcel(accountId: AccountId, postmaster: Speaker, parcelId: Long) {
    val office = officeAtWork(postmaster) ?: return closed(accountId)
    val customer = customerOf(accountId)

    jobs.submit(customer.masterId) {
      parcels.refuse(customer, parcelId)?.let { refuse(accountId, it.code, it.args) }
      showParcels(accountId, customer.masterId, postmaster, office)
    }
  }

  /** On the tick, which knows what the courier's bag can still take. */
  fun takeParcels(world: World, accountId: AccountId, postmaster: Speaker, parcelIds: List<Long>) {
    val office = officeAtWork(postmaster) ?: return closed(accountId)
    val entityId = connections.getSelectedMasterEntityId(accountId)
    val bag = world.get(entityId, Inventory::class) ?: return
    val capacity = world.get(entityId, CarryCapacity::class) ?: return
    val courier = CourierService.Courier(connections.getMasterId(accountId), entityId, capacity.max - bag.totalWeight)

    jobs.submit(courier.masterId) {
      couriers.take(courier, office, parcelIds)?.let { refuse(accountId, it.code, it.args) }
      showRouteBoard(accountId, postmaster, office)
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

  private fun showParcels(accountId: AccountId, masterId: Long, postmaster: Speaker, office: PostOffices.Office) {
    out.sendToPlayer(accountId, ParcelsSMSG(postmaster.entityId, parcels.parcelsOf(masterId, office)))
  }

  private fun showRouteBoard(
    accountId: AccountId,
    postmaster: Speaker,
    office: PostOffices.Office,
    delivered: CourierService.Delivery? = null,
  ) {
    out.sendToPlayer(accountId, RouteBoardSMSG(postmaster.entityId, couriers.routeBoard(office), delivered))
  }

  private fun closed(accountId: AccountId) {
    refuse(accountId, OpError.POST_OFFICE_CLOSED)
  }

  private fun refuse(accountId: AccountId, code: OpError, args: List<String> = emptyList()) {
    out.sendToPlayer(accountId, OperationErrorSMSG(code, args))
  }

  private fun officeAtWork(postmaster: Speaker): PostOffices.Office? {
    if (postmaster.business != PostOffices.POST_OFFICE) return null
    if (!postmaster.merchant.hours.isOpenAt(clock.now().minuteOfDay)) return null

    return offices.officeOf(postmaster.settlement)
  }

  private fun answer(
    accountId: AccountId,
    masterId: Long,
    postmaster: Speaker,
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
      PostOfficeOpenSMSG(postmaster.entityId, office.name, office.level, homeName, isHome = home == office.settlement)
    )
    val courier = CourierService.Courier(masterId, connections.getSelectedMasterEntityId(accountId), freeWeight = 0)
    val delivered = couriers.deliver(courier, office)
    showParcels(accountId, masterId, postmaster, office)
    showRouteBoard(accountId, postmaster, office, delivered)
  }
}
