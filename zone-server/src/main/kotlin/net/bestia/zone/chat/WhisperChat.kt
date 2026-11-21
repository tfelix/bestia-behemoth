package net.bestia.zone.chat

import net.bestia.zone.account.persistence.Master
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

/** Where a whisper goes: to the master it names, and back to its writer as proof that it arrived. */
@Component
class WhisperChat(
  private val masters: MasterRepository,
  private val sessions: ConnectionInfoService,
  private val outMessageProcessor: OutMessageProcessor,
) {

  /**
   * False when the whisper had nowhere to go: no master is called [targetName], or nobody is playing it. The
   * caller owes the writer that answer.
   */
  fun deliver(writer: Master, targetName: String, text: String): Boolean {
    val target = masters.findByName(targetName) ?: return false
    if (!sessions.isPlaying(target.account.id, target.id)) {
      return false
    }

    val heard = ChatSMSG(type = ChatType.WHISPER, text = text, senderUsername = writer.name)
    outMessageProcessor.sendToPlayer(target.account.id, heard)
    // The stored spelling of the name, so the writer's log shows who really got it.
    outMessageProcessor.sendToPlayer(writer.account.id, heard.copy(targetUsername = target.name))

    return true
  }
}
