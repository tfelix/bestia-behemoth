package net.bestia.zone.chat

import net.bestia.bus.AppPresence
import net.bestia.bus.EventPublisher
import net.bestia.bus.contract.ZoneEvent
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

/**
 * Where a whisper goes, wherever it was written: to the master it names, and back to its writer as proof that it
 * arrived. The game hears it where the master is played; the app always gets it, like party and guild lines.
 */
@Component
class WhisperChat(
  private val masters: MasterRepository,
  private val sessions: ConnectionInfoService,
  private val outMessageProcessor: OutMessageProcessor,
  private val appPresence: AppPresence,
  private val events: EventPublisher,
) {

  /**
   * False when the whisper had nowhere to go: no master is called [targetName], or its player is neither playing it
   * nor has an app open. A whisper does not wait for anybody; that is what a letter is for. The caller owes the
   * writer that answer.
   */
  fun deliver(writer: Master, targetName: String, text: String): Boolean {
    val target = masters.findByName(targetName) ?: return false
    val targetInGame = isPlayed(target)
    if (!targetInGame && !appPresence.isOpen(target.account.id)) {
      return false
    }

    val heard = ChatSMSG(type = ChatType.WHISPER, text = text, senderUsername = writer.name)
    if (targetInGame) {
      outMessageProcessor.sendToPlayer(target.account.id, heard)
    }
    // The stored spelling of the name, so the writer's log shows who really got it.
    if (isPlayed(writer)) {
      outMessageProcessor.sendToPlayer(writer.account.id, heard.copy(targetUsername = target.name))
    }

    val sentAt = System.currentTimeMillis()
    events.publish(ZoneEvent.ChatLine(target.account.id, target.id, WHISPER, writer.name, text, sentAt))
    events.publish(ZoneEvent.ChatLine(writer.account.id, writer.id, WHISPER, writer.name, text, sentAt, target.name))

    return true
  }

  private fun isPlayed(master: Master): Boolean {
    return sessions.isPlaying(master.account.id, master.id)
  }

  private companion object {
    val WHISPER = ChatType.WHISPER.name
  }
}
