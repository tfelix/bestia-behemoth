package net.bestia.zone.chat.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.zone.chat.ChatCommand
import net.bestia.zone.water.WaterService
import net.bestia.zone.world.stream.ChunkStreamConfig
import org.springframework.stereotype.Component

/**
 * `/water <x> <y> <z> [radius]` - pours a bowl of water into air. The development trigger for water, the way
 * `/carve` is for mining, and gated by the same `chunk-stream.allow-debug-edits`.
 */
@Component
class WaterChatCommand(
  private val water: WaterService,
  private val settings: ChunkStreamConfig
) : ChatCommand() {

  override val requiredAuthority: Authority = Authority.TERRAIN

  override fun getHelpText(): String {
    return "/water <X> <Y> <Z> [RADIUS] - Pours the lower half of a sphere of water into air. Z is the global " +
        "vertical index, 0 is sea level. RADIUS is in voxels, 1 to ${WaterService.MAX_POUR_RADIUS}, and " +
        "defaults to $DEFAULT_RADIUS."
  }

  override fun isMatch(cmdText: String): Boolean {
    return CMD_REGEX.matches(cmdText.trim())
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    if (!settings.allowDebugEdits) {
      LOG.info { "Refused /water from $playerId: chunk-stream.allow-debug-edits is off" }
      return false
    }

    val match = CMD_REGEX.find(cmdText.trim()) ?: return false

    val x = match.groupValues[1].toLong()
    val y = match.groupValues[2].toLong()
    val z = match.groupValues[3].toLong()
    val radius = match.groupValues[4].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: DEFAULT_RADIUS

    if (radius !in 1..WaterService.MAX_POUR_RADIUS) {
      LOG.warn { "Refused /water from $playerId: radius $radius is outside 1..${WaterService.MAX_POUR_RADIUS}" }
      return false
    }

    water.requestPour(x, y, z, radius)
    LOG.info { "Queued /water ($x,$y,$z) r=$radius for player $playerId" }

    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    const val DEFAULT_RADIUS = 3

    private val CMD_REGEX = Regex("""^/water\s+(-?\d+)\s+(-?\d+)\s+(-?\d+)(?:\s+(\d+))?$""")
  }
}
