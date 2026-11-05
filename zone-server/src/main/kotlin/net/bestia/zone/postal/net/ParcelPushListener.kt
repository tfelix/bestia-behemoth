package net.bestia.zone.postal.net

import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.postal.parcel.ParcelArrivedEvent
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/** Tells the game a parcel arrived, while its recipient is the master being played. */
@Component
class ParcelPushListener(
  private val connections: ConnectionInfoService,
  private val out: OutMessageProcessor,
) {

  @EventListener
  fun onArrived(event: ParcelArrivedEvent) {
    if (!connections.hasActiveSession(event.recipientAccountId)) return
    if (connections.activeMasterIdOf(event.recipientAccountId) != event.recipientMasterId) return

    out.sendToPlayer(event.recipientAccountId, ParcelArrivedSMSG(event.senderName, event.officeName))
  }
}
