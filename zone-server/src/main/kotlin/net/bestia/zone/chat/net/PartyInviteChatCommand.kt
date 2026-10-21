package net.bestia.zone.chat.net

import net.bestia.zone.party.net.RequestPartyInvitationCMSG
import net.bestia.zone.party.net.RequestInvitePlayerToPartyHandler
import org.springframework.stereotype.Component
import net.bestia.zone.chat.ChatCommand

/** `/invite <username>` - invites the named master into the sender's party. */
@Component
class PartyInviteChatCommand(
  private val requestInvitePlayerToPartyHandler: RequestInvitePlayerToPartyHandler,
) : ChatCommand() {

  companion object {
    private val CMD_REGEX = Regex("""^/invite\s+(\S+)$""")
  }

  override fun getHelpText(): String {
    return "/invite <username> - Invites a player into your party."
  }

  override fun isMatch(cmdText: String): Boolean {
    return CMD_REGEX.matches(cmdText.trim())
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    val match = CMD_REGEX.find(cmdText.trim()) ?: return false

    requestInvitePlayerToPartyHandler.handle(RequestPartyInvitationCMSG(playerId, match.groupValues[1]))

    return true
  }
}
