package net.bestia.zone.chat

import net.bestia.zone.party.CreatePartyCMSG
import net.bestia.zone.party.handler.CreatePartyHandler
import org.springframework.stereotype.Component

/**
 * `/party <name>` - creates a new party owned by the command's sender. Name validation (ASCII,
 * trimmed, max configurable length) happens in [net.bestia.zone.party.PartyService.createParty],
 * the single source of truth also used by non-chat callers.
 *
 * Calls [CreatePartyHandler] directly rather than going through [net.bestia.zone.message.InMessageProcessor]:
 * that processor depends (transitively, via ChatHandler/ChatCommandHandler) on every [ChatCommand],
 * so routing back through it here would be a circular Spring bean dependency.
 */
@Component
class PartyCreateChatCommand(
  private val createPartyHandler: CreatePartyHandler,
) : ChatCommand() {

  companion object {
    private val CMD_REGEX = Regex("""^/party\s+(.+)$""")
  }

  override fun getHelpText(): String {
    return "/party <name> - Creates a new party with the given name."
  }

  override fun isMatch(cmdText: String): Boolean {
    return CMD_REGEX.matches(cmdText.trim())
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    val match = CMD_REGEX.find(cmdText.trim()) ?: return false
    val partyName = match.groupValues[1].trim()

    createPartyHandler.handle(CreatePartyCMSG(playerId, partyName))

    return true
  }
}
