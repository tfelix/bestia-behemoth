package net.bestia.zone.chat.net

import net.bestia.account.Authority
import net.bestia.worldgen.core.Faction
import net.bestia.zone.chat.ChatCommand
import net.bestia.zone.chat.ChatSMSG
import net.bestia.zone.chat.ChatType
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.identity.ecs.FactionMembership
import net.bestia.zone.faction.FactionService
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.time.Duration

/** Swears the caller's master to an Order or frees it, so the Order rules can be tested before the quest exists. */
@Component
class FactionChatCommand(
  private val connectionInfoService: ConnectionInfoService,
  private val factions: FactionService,
  private val world: WorldView,
  private val out: OutMessageProcessor,
) : ChatCommand() {

  override val requiredAuthority: Authority = Authority.FACTIONS

  override fun getHelpText(): String {
    return "/faction - Your Order. /faction swear <ORDER> - Swears you to ${Faction.entries.joinToString(", ")}. " +
      "/faction leave - Frees you at once, without the time unsworn."
  }

  override fun isMatch(cmdText: String): Boolean {
    return CMD_REGEX.matches(cmdText.trim())
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    val match = CMD_REGEX.find(cmdText.trim()) ?: return false
    val masterEntity = connectionInfoService.getSelectedMasterEntityId(playerId)
    val (verb, argument) = match.destructured

    val answer = when (verb) {
      "" -> world.modify(masterEntity) { id -> describe(this, id) }
      "swear" -> {
        val faction = Faction.entries.firstOrNull { it.name == argument.uppercase() } ?: return false
        world.modify(masterEntity) { id ->
          factions.swear(this, id, faction)?.let { "Refused: $it." } ?: describe(this, id)
        }
      }
      "leave" -> world.modify(masterEntity) { id ->
        if (factions.leave(this, id, Duration.ZERO)) describe(this, id) else "You are not sworn."
      }
      else -> return false
    }

    out.sendToPlayer(playerId, ChatSMSG(text = answer ?: "No master in the world.", type = ChatType.COMMAND))

    return true
  }

  private fun describe(world: World, id: EntityId): String {
    val membership = world.get(id, FactionMembership::class) ?: FactionMembership()
    val faction = membership.faction ?: return "Unsworn" + (membership.unswornUntil?.let { " until $it." } ?: ".")

    return "Sworn to ${faction.label} since ${membership.swornAt}."
  }

  private companion object {
    val CMD_REGEX = Regex("""^/faction(?:\s+(swear|leave)(?:\s+(\S+))?)?$""")
  }
}
