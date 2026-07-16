package net.bestia.zone.chat

import net.bestia.zone.account.master.MasterNotFoundException
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.party.PartyErrorSMSG
import net.bestia.zone.party.RequestPartyInvitationCMSG
import net.bestia.zone.party.handler.RequestInvitePlayerToPartyHandler
import org.springframework.stereotype.Component

/** `/invite <username>` - invites the named master's account into the sender's party. */
@Component
class PartyInviteChatCommand(
  private val requestInvitePlayerToPartyHandler: RequestInvitePlayerToPartyHandler,
  private val masterResolver: MasterResolver,
  private val outMessageProcessor: OutMessageProcessor,
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
    val targetMasterName = match.groupValues[1]

    val targetAccountId = try {
      masterResolver.getAccountIdByMasterName(targetMasterName)
    } catch (_: MasterNotFoundException) {
      outMessageProcessor.sendToPlayer(playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.PLAYER_NOT_FOUND))
      return true
    }

    requestInvitePlayerToPartyHandler.handle(RequestPartyInvitationCMSG(playerId, targetAccountId))

    return true
  }
}
