package net.bestia.zone.companion

import net.bestia.bus.RequestHandler
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusRequest
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.companion.query.BestiaQueries
import net.bestia.zone.companion.query.CatalogQueries
import net.bestia.zone.companion.query.ChatCommand
import net.bestia.zone.companion.query.CompendiumQueries
import net.bestia.zone.companion.query.InventoryQueries
import net.bestia.zone.companion.query.MapQueries
import net.bestia.zone.companion.query.MasterQueries
import net.bestia.zone.companion.query.PartyQueries
import net.bestia.zone.companion.query.StanceCommand
import net.bestia.zone.companion.query.TicketQueries
import org.springframework.stereotype.Component

/** Routes each question from the api-service to the query that answers it. Runs on a bus worker thread. */
@Component
class CompanionRequestDispatcher(
  private val masters: MasterQueries,
  private val bestias: BestiaQueries,
  private val inventory: InventoryQueries,
  private val parties: PartyQueries,
  private val catalog: CatalogQueries,
  private val map: MapQueries,
  private val stance: StanceCommand,
  private val tickets: TicketQueries,
  private val chat: ChatCommand,
  private val compendium: CompendiumQueries,
) : RequestHandler {

  override fun handle(request: BusRequest): BusReply {
    val id = request.correlationId
    val account = request.accountId

    return when (val query = request.query) {
      ZoneQuery.ListMasters -> BusReply.ok(id, masters.list(account))
      is ZoneQuery.MasterSheet -> found(id, masters.sheet(account, query.masterId))
      is ZoneQuery.BestiaRoster -> found(id, bestias.roster(account, query.masterId))
      is ZoneQuery.Inventory -> found(id, inventory.bag(account, query.masterId))
      is ZoneQuery.PartyRoster -> found(id, parties.party(account, query.masterId))
      is ZoneQuery.Compendium -> found(id, compendium.compendium(account, query.masterId))
      ZoneQuery.Catalog -> BusReply.ok(id, catalog.catalog())
      ZoneQuery.MapMeta -> map.meta(id)
      is ZoneQuery.MapTile -> map.tile(id, account, query)
      is ZoneQuery.MapPlaces -> map.places(id, account, query)
      is ZoneQuery.MapServices -> map.services(id, account, query)
      is ZoneQuery.HabitatTile -> map.habitat(id, account, query)
      is ZoneQuery.WhoseTicket -> tickets.owner(id, query)
      is ZoneQuery.SetStance -> stance.apply(id, account, query)
      is ZoneQuery.SendChat -> chat.send(id, account, query)
    }
  }

  private fun found(correlationId: String, answer: ZoneAnswer?): BusReply {
    if (answer == null) {
      return BusReply.of(correlationId, BusReply.Status.NOT_FOUND)
    }
    return BusReply.ok(correlationId, answer)
  }
}
