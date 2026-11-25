package net.bestia.zone.chat.net

import net.bestia.account.Authority
import net.bestia.zone.chat.ChatCommand
import net.bestia.zone.chat.ChatSMSG
import net.bestia.zone.chat.ChatType
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.worldevent.ProvinceSignalSnapshot
import net.bestia.zone.worldevent.WorldEventCatalogue
import net.bestia.zone.worldevent.WorldEventDirector
import net.bestia.zone.worldevent.ProvinceSignals
import org.springframework.stereotype.Component
import java.util.Locale

/** Lists, starts and ends world events, to watch the director's choices without waiting for them. */
@Component
class EventChatCommand(
  private val connectionInfoService: ConnectionInfoService,
  private val director: WorldEventDirector,
  private val catalogue: WorldEventCatalogue,
  private val snapshot: ProvinceSignalSnapshot,
  private val world: WorldView,
  private val out: OutMessageProcessor,
) : ChatCommand() {

  override val requiredAuthority: Authority = Authority.WORLD_EVENTS

  override fun getHelpText(): String {
    return "/event - The running events and the budget where you stand. /event force <ID> - Starts an event in " +
      "your province now. /event cancel <INSTANCE> - Ends a running event. Events: " +
      catalogue.all().joinToString(", ") { it.id }
  }

  override fun isMatch(cmdText: String): Boolean {
    return CMD_REGEX.matches(cmdText.trim())
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    val match = CMD_REGEX.find(cmdText.trim()) ?: return false
    val (verb, argument) = match.destructured

    val answer = when (verb) {
      "" -> describe(playerId)
      "force" -> force(playerId, argument) ?: return false
      "cancel" -> {
        val id = argument.toLongOrNull() ?: return false
        if (director.cancel(id)) "Ended event $id." else "No running event $id."
      }
      else -> return false
    }

    out.sendToPlayer(playerId, ChatSMSG(text = answer, type = ChatType.COMMAND))
    return true
  }

  private fun describe(playerId: Long): String {
    val running = director.running().joinToString("; ") {
      "#${it.id} ${it.eventId} in province ${it.provinceIndex} at (${it.x}, ${it.y})"
    }.ifEmpty { "none" }
    val here = provinceOf(playerId)
    val budget = here?.let { String.format(Locale.ROOT, "%.2f", director.budgetOf(it.province.index)) } ?: "-"

    return "Running: $running. Here: ${here?.province?.name ?: "no province"}, budget $budget."
  }

  private fun force(playerId: Long, eventId: String): String? {
    val entry = catalogue.of(eventId) ?: return null
    val here = provinceOf(playerId) ?: return "You stand in no province."
    val started = director.force(entry, here) ?: return "${entry.id} cannot start in ${here.province.name}."

    return "Started ${entry.id} as #${started.id} at (${started.x}, ${started.y})."
  }

  /** What the director knows about the province the caller stands in. */
  private fun provinceOf(playerId: Long): ProvinceSignals? {
    val at = world.read {
      get(connectionInfoService.getSelectedMasterEntityId(playerId), Position::class)?.toVec3L()
    } ?: return null
    val index = snapshot.provinceIndexAt(at.x.toDouble(), at.y.toDouble()) ?: return null

    return snapshot.take().firstOrNull { it.province.index == index }
  }

  private companion object {
    val CMD_REGEX = Regex("""^/event(?:\s+(force|cancel)\s+(\S+))?$""")
  }
}
