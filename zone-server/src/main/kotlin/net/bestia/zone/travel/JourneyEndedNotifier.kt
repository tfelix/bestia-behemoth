package net.bestia.zone.travel

import net.bestia.zone.message.OutMessageProcessor
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/** Tells a player how a journey of one of its own entities ended, wherever it was. */
@Component
class JourneyEndedNotifier(
  private val outMessageProcessor: OutMessageProcessor,
) {

  @EventListener
  fun onJourneyEnded(event: JourneyEndedEvent) {
    if (event.order != JourneyOrder.PLAYER) return
    val accountId = event.accountId ?: return

    outMessageProcessor.sendToPlayer(
      accountId,
      JourneyEndedSMSG(event.entityId, event.failure, event.position, event.name.orEmpty()),
    )
  }
}
