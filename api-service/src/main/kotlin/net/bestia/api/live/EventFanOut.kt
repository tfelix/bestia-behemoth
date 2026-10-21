package net.bestia.api.live

import net.bestia.api.cache.AnswerCache
import net.bestia.api.cache.TileCache
import net.bestia.bus.StreamedEvent
import net.bestia.bus.contract.ZoneEvent
import net.bestia.bus.valkey.ValkeyEventReader
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Follows the zone's event stream: every event drops the account's cached answers and goes to its open sockets.
 * Each instance follows the whole stream, because any of them may hold a socket of the account.
 */
@Component
class EventFanOut(
  private val events: ValkeyEventReader,
  private val sessions: LiveSessions,
  private val answers: AnswerCache,
  private val tiles: TileCache,
) {

  @EventListener(ApplicationReadyEvent::class)
  fun start() {
    events.tail("fan-out", ::deliver)
  }

  fun deliver(streamed: StreamedEvent) {
    val event = streamed.event
    answers.forget(event.accountId)
    if (event is ZoneEvent.ChartsChanged) {
      tiles.forgetMaster(event.masterId)
    }

    sessions.send(event.accountId, CompanionSocketHandler.EventFrame(event = LiveEvent.of(streamed)))
  }
}
