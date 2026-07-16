package net.bestia.zone.chat

import net.bestia.zone.party.LeavePartyCMSG
import net.bestia.zone.party.handler.LeavePartyHandler
import org.springframework.stereotype.Component

/** `/leave` - leaves the sender's current party. Disbands it instead if the sender is the owner. */
@Component
class PartyLeaveChatCommand(
  private val leavePartyHandler: LeavePartyHandler,
) : ChatCommand() {

  override fun getHelpText(): String {
    return "/leave - Leaves your current party."
  }

  override fun isMatch(cmdText: String): Boolean {
    return cmdText.trim() == "/leave"
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    leavePartyHandler.handle(LeavePartyCMSG(playerId))

    return true
  }
}
