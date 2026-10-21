package net.bestia.api.live

import net.bestia.api.auth.TicketOwners
import net.bestia.api.cache.AnswerCache
import net.bestia.api.cache.TileCache
import net.bestia.bus.StreamEntry
import net.bestia.bus.contract.ZoneEvent
import net.bestia.bus.valkey.ValkeyStreamReader
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Follows the zone's event stream: every event goes to the account's open sockets, and all but chat drop its cached
 * answers. Each instance follows the whole stream, because any of them may hold a socket of the account.
 */
@Component
class EventFanOut(
  private val events: ValkeyStreamReader<ZoneEvent>,
  private val sessions: LiveSessions,
  private val answers: AnswerCache,
  private val tiles: TileCache,
  private val tickets: TicketOwners,
) {

  @EventListener(ApplicationReadyEvent::class)
  fun start() {
    events.tail("fan-out", ::deliver)
  }

  fun deliver(streamed: StreamEntry<ZoneEvent>) {
    val event = streamed.payload
    // A chat line changes no answer, and a busy guild would otherwise keep its members' caches empty.
    if (event !is ZoneEvent.ChatLine) {
      answers.forget(event.accountId)
    }
    if (event is ZoneEvent.ChartsChanged) {
      tiles.forgetMaster(event.masterId)
    }
    // Leaving the world closed the socket, and its ticket died with it.
    if (event is ZoneEvent.Presence && !event.inWorld) {
      tickets.forgetAccount(event.accountId)
    }

    sessions.send(event.accountId, CompanionSocketHandler.EventFrame(event = LiveEvent.of(streamed)))
  }
}
