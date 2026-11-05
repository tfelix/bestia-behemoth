package net.bestia.zone.companion.query

import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusReply.Status
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.postal.parcel.ParcelService
import org.springframework.stereotype.Service

/** A master's parcels, for the app to watch them come. Collecting needs the master at a post office, in the game. */
@Service
class ParcelQueries(
  private val owned: OwnedMasters,
  private val parcels: ParcelService,
  private val items: ItemRepository,
) {

  fun parcels(correlationId: String, accountId: Long, query: ZoneQuery.Parcels): BusReply {
    owned.find(accountId, query.masterId) ?: return BusReply.of(correlationId, Status.NOT_FOUND)

    val views = parcels.parcelsOf(query.masterId, at = null)
    val identifiers = items.findAllById(views.flatMap { view -> view.goods.map { it.itemId } }.toSet())
      .associate { it.id to it.identifier }

    val answer = views.map { parcel ->
      ZoneAnswer.Parcels.Parcel(
        parcelId = parcel.parcelId,
        senderName = parcel.senderName,
        destinationName = parcel.destinationName,
        latestArrivalEpochMs = parcel.latestArrival.toEpochMilli(),
        arrived = parcel.arrived,
        returning = parcel.returning,
        goods = parcel.goods.map { ZoneAnswer.Parcels.Goods(identifiers[it.itemId].orEmpty(), it.amount) },
      )
    }

    return BusReply.ok(correlationId, ZoneAnswer.Parcels(answer))
  }
}
