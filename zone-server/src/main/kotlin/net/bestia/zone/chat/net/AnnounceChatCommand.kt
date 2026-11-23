package net.bestia.zone.chat.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.zone.chat.ChatCommand
import net.bestia.zone.chat.ChatSMSG
import net.bestia.zone.chat.ChatType
import net.bestia.zone.message.AnnouncementSMSG
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

/** `/announce <text>` - tells every connected player, in the chat and on a banner. */
@Component
class AnnounceChatCommand(
  private val out: OutMessageProcessor,
) : ChatCommand() {

  override val requiredAuthority: Authority = Authority.ANNOUNCE

  override fun getHelpText() = "/announce <text> - Shows the text to everyone online, in the chat and on a banner."

  override fun isMatch(cmdText: String) = CMD_REGEX.matches(cmdText.trim())

  override fun execute(playerId: Long, cmdText: String): Boolean {
    val text = CMD_REGEX.find(cmdText.trim())?.groupValues?.get(1)?.trim().orEmpty()
    if (text.isEmpty()) {
      out.sendToPlayer(playerId, ChatSMSG(text = getHelpText(), type = ChatType.COMMAND))
      return true
    }

    val told = out.sendToAllConnected(AnnouncementSMSG(AnnouncementSMSG.Announcement.GM, listOf(text)))
    LOG.info { "Player $playerId announced to $told players: $text" }

    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
    private val CMD_REGEX = Regex("^/announce(?:\\s+(.*))?$", RegexOption.DOT_MATCHES_ALL)
  }
}
