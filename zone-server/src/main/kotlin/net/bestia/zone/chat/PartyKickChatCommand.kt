package net.bestia.zone.chat

import net.bestia.zone.account.master.MasterNotFoundException
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.party.PartyErrorSMSG
import net.bestia.zone.party.PartyService
import net.bestia.zone.party.RemovePartyMemberCMSG
import net.bestia.zone.party.handler.RemovePartyMemberHandler
import org.springframework.stereotype.Component

/** `/kick <username>` - the party owner removes the named member from their party. */
@Component
class PartyKickChatCommand(
  private val removePartyMemberHandler: RemovePartyMemberHandler,
  private val masterResolver: MasterResolver,
  private val partyService: PartyService,
  private val outMessageProcessor: OutMessageProcessor,
) : ChatCommand() {

  companion object {
    private val CMD_REGEX = Regex("""^/kick\s+(\S+)$""")
  }

  override fun getHelpText(): String {
    return "/kick <username> - Removes a player from your party (owner only)."
  }

  override fun isMatch(cmdText: String): Boolean {
    return CMD_REGEX.matches(cmdText.trim())
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    val match = CMD_REGEX.find(cmdText.trim()) ?: return false
    val targetMasterName = match.groupValues[1]

    val partyId = partyService.findPartyOfAccount(playerId)?.id
    if (partyId == null) {
      outMessageProcessor.sendToPlayer(playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.NO_PARTY))
      return true
    }

    val targetAccountId = try {
      masterResolver.getAccountIdByMasterName(targetMasterName)
    } catch (_: MasterNotFoundException) {
      outMessageProcessor.sendToPlayer(playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.PLAYER_NOT_FOUND))
      return true
    }

    removePartyMemberHandler.handle(RemovePartyMemberCMSG(playerId, partyId, targetAccountId))

    return true
  }
}
