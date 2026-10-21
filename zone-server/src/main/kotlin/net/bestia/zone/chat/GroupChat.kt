package net.bestia.zone.chat

import net.bestia.bus.EventPublisher
import net.bestia.bus.contract.ZoneEvent
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.identity.GroupMember
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

/**
 * Where a party or guild line goes, wherever it was said: the game client's chat and the companion app's are the
 * same conversation, so both say a line through here.
 */
@Component
class GroupChat(
  private val outMessageProcessor: OutMessageProcessor,
  private val sessions: ConnectionInfoService,
  private val events: EventPublisher,
) {

  /**
   * Every member being played hears [text] in the game wherever they stand, the sender too, so the sender sees the
   * line land. No speech bubble, so no entity id. Every member, played or not, also gets it in the app: a member
   * who is away reads it there, and one who is playing may have the app open as well.
   */
  fun deliver(type: ChatType, senderName: String, text: String, members: Collection<GroupMember>) {
    require(type == ChatType.PARTY || type == ChatType.GUILD) { "Not a group chat type: $type" }

    val line = ChatSMSG(text = text, type = type, senderUsername = senderName)
    members
      .filter { sessions.isPlaying(it.accountId, it.masterId) }
      .mapTo(HashSet()) { it.accountId }
      .forEach { outMessageProcessor.sendToPlayer(it, line) }

    val sentAt = System.currentTimeMillis()
    for (member in members) {
      events.publish(ZoneEvent.ChatLine(member.accountId, member.masterId, type.name, senderName, text, sentAt))
    }
  }
}
