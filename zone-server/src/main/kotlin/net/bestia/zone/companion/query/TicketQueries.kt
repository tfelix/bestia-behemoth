package net.bestia.zone.companion.query

import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusReply.Status
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.session.HttpTicketService
import org.springframework.stereotype.Component

/**
 * Whose game connection a ticket belongs to, so the api-service can serve the game client's map. A ticket is
 * minted at the socket handshake and dropped when the socket closes, so it proves a live connection and nothing
 * more; the api-service accepts it for the map only.
 */
@Component
class TicketQueries(private val tickets: HttpTicketService) {

  fun owner(correlationId: String, query: ZoneQuery.WhoseTicket): BusReply {
    val accountId = tickets.accountFor(query.ticket) ?: return BusReply.of(correlationId, Status.NOT_FOUND)
    return BusReply.ok(correlationId, ZoneAnswer.TicketOwner(accountId))
  }
}
