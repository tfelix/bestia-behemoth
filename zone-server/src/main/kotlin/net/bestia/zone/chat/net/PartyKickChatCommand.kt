package net.bestia.zone.chat.net

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.party.net.RemovePartyMemberCMSG
import net.bestia.zone.party.net.RemovePartyMemberHandler
import org.springframework.stereotype.Component
import net.bestia.zone.chat.ChatCommand

/** `/kick <username>` - the party owner removes the named member from their party. */
@Component
class PartyKickChatCommand(
  private val removePartyMemberHandler: RemovePartyMemberHandler,
  private val masterRepository: MasterRepository,
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

    // A member need not be online to be kicked, so the name is looked up rather than asked of the session.
    val target = masterRepository.findByName(targetMasterName)
    if (target == null) {
      outMessageProcessor.sendToPlayer(
        playerId,
        OperationErrorSMSG(OpError.PARTY_NOT_A_MEMBER, listOf(targetMasterName))
      )
      return true
    }

    removePartyMemberHandler.handle(RemovePartyMemberCMSG(playerId, target.id))

    return true
  }
}
