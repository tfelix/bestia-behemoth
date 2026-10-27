package net.bestia.zone.chat.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.zone.cartography.SurveyService
import net.bestia.zone.cartography.chart.ChartService
import net.bestia.zone.cartography.coverage.Coverage
import net.bestia.zone.chat.ChatCommand
import net.bestia.zone.chat.ChatSMSG
import net.bestia.zone.chat.ChatType
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component

/**
 * `/reveal` charts the whole world, `/reveal <metres>` a disc around the caller - as a chart put in the master's
 * bag, so both the game map and the companion's lift their fog the way a survey would. Dropping the chart puts
 * the fog back.
 */
@Component
class RevealMapChatCommand(
  private val connections: ConnectionInfoService,
  private val world: WorldView,
  private val worldService: WorldService,
  private val charts: ChartService,
  private val surveys: SurveyService,
  private val out: OutMessageProcessor,
) : ChatCommand() {

  override val requiredAuthority: Authority = Authority.MAP_REVEAL

  override fun getHelpText(): String {
    return "/reveal [metres] - Puts a chart of the whole world in your bag, or of a disc that wide around you."
  }

  override fun isMatch(cmdText: String): Boolean {
    return CMD_REGEX.matches(cmdText.trim())
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    val radius = CMD_REGEX.find(cmdText.trim())?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }?.toDouble()
    val masterId = connections.activeMasterIdOf(playerId) ?: return false

    val coverage = Coverage(charts.grid)
    if (radius == null) {
      coverage.fillAll()
    } else {
      val entityId = connections.getActiveEntityId(playerId)
      val at = world.read { get(entityId, Position::class) } ?: return false
      val voxelSize = worldService.config.voxelSize
      coverage.fillDisc(at.x * voxelSize, at.y * voxelSize, radius)
    }

    val text = when (val result = charts.grantGmChart(masterId, coverage)) {
      is ChartService.Result.Ok -> {
        surveys.applyToLiveInventory(connections.getSelectedMasterEntityId(playerId), result)
        "A chart of ${result.cells} cells is in your bag."
      }
      is ChartService.Result.Refused -> "No chart: ${result.error}"
    }
    out.sendToPlayer(playerId, ChatSMSG(text = text, type = ChatType.COMMAND))
    LOG.info { "/reveal ${radius ?: "world"} for player $playerId: $text" }

    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
    private val CMD_REGEX = Regex("""^/reveal(?:\s+(\d+(?:\.\d+)?))?$""")
  }
}
