package net.bestia.zone.chat.net

import net.bestia.account.Authority
import net.bestia.zone.chat.ChatCommand
import net.bestia.zone.chat.ChatSMSG
import net.bestia.zone.chat.ChatType
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.honor.HonorAct
import net.bestia.zone.honor.HonorService
import net.bestia.zone.honor.ecs.Honor
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Component

/**
 * Shows and sets the caller's honor, and commits an act in their name. Most acts have nothing in the game that
 * commits them yet, so this is the only way to see their numbers at work.
 */
@Component
class HonorChatCommand(
  private val connectionInfoService: ConnectionInfoService,
  private val honorService: HonorService,
  private val sites: SettlementSiteIndex,
  private val world: WorldView,
  private val out: OutMessageProcessor,
) : ChatCommand() {

  override val requiredAuthority: Authority = Authority.HONOR

  override fun getHelpText(): String {
    return "/honor - Your honor here. /honor set <VALUE> - Sets your global honor. " +
      "/honor act <ACT> - Commits an act where you stand: ${HonorAct.entries.joinToString(", ")}"
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
      "set" -> {
        val value = argument.toIntOrNull() ?: return false
        world.modify(masterEntity) { id ->
          honorService.setGlobal(this, id, value)
          describe(this, id)
        }
      }
      "act" -> {
        val act = HonorAct.entries.firstOrNull { it.name == argument.uppercase() } ?: return false
        world.modify(masterEntity) { id ->
          val at = get(id, Position::class)?.toVec3L() ?: return@modify "You are nowhere."
          val recorded = honorService.record(this, act, id, at)
          if (recorded.seen) {
            "$act: global ${recorded.globalChange}, ${recorded.grudges.size} witness(es), " +
              "settlement ${recorded.settlement ?: "none"}. " + describe(this, id)
          } else {
            "$act: nobody saw it."
          }
        }
      }
      else -> return false
    }

    out.sendToPlayer(playerId, ChatSMSG(text = answer ?: "No master in the world.", type = ChatType.COMMAND))

    return true
  }

  private fun describe(world: World, id: Long): String {
    val global = world.get(id, Honor::class)?.shownValue ?: return "No honor."
    val masterId = world.get(id, Master::class)?.masterId ?: return "No honor."
    val at = world.get(id, Position::class)?.toVec3L()
    val settlement = at?.let { sites.siteCovering(it.x, it.y)?.index }
      ?: return "Global honor $global. Not in a settlement."

    val local = honorService.settlementValue(masterId, settlement)

    return "Global honor $global. Settlement $settlement: local $local, combined ${global + local}."
  }

  private companion object {
    val CMD_REGEX = Regex("""^/honor(?:\s+(set|act)\s+(\S+))?$""")
  }
}
