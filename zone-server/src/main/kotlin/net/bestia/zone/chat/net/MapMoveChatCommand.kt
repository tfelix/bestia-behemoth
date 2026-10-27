package net.bestia.zone.chat.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.geo.GridRef
import net.bestia.zone.chat.ChatCommand
import net.bestia.zone.chat.ChatSMSG
import net.bestia.zone.chat.ChatType
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.place.ecs.AreaNameRegistry
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkStreamInbox
import org.springframework.stereotype.Component
import kotlin.math.roundToLong

/**
 * `/mm <target>` - moves the caller's active entity across the map, landing them on the ground. The target is a
 * grid reference (`/mm H12 41 37`), a settlement's name (`/mm Trestein`) or raw voxels (`/mm 64000 64000`).
 *
 * Exists because the world is 128 km across and a player starts in the middle of it with a walking speed
 * measured in metres per second. Terrain, biomes, rivers and settlements are all things you have to be
 * standing near to look at, and without this the only way to see any of them was to regenerate the world with
 * a seed that happened to put something interesting next to the spawn.
 *
 * The elevation is not a parameter, deliberately. A caller who had to supply it would have to know the terrain
 * height first, which is the thing they are travelling to find out; and naming it wrong puts them inside a
 * mountain, which looks exactly like the renderer being broken. [ChunkStreamInbox.Teleport] resolves it against
 * the heightfield instead.
 *
 * Queued rather than applied, for the same reason as [CarveChatCommand]: chat is dispatched on a Netty
 * worker thread, and both the terrain the elevation comes from and the tick that re-anchors the player's view
 * belong to `zone-tick`.
 */
@Component
class MapMoveChatCommand(
  private val inbox: ChunkStreamInbox,
  private val worldService: WorldService,
  private val areaNames: AreaNameRegistry,
  private val out: OutMessageProcessor,
) : ChatCommand() {

  override val requiredAuthority: Authority = Authority.MAP_MOVE

  override fun getHelpText(): String {
    return "/mm <target> - Moves you to a map position, standing on the ground there. The target is a grid " +
        "reference (/mm H12 41 37), a settlement (/mm Trestein) or voxels (/mm 64000 64000)."
  }

  override fun isMatch(cmdText: String): Boolean {
    return CMD_REGEX.matches(cmdText.trim())
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    val target = CMD_REGEX.find(cmdText.trim())?.groupValues?.get(1) ?: return false

    val voxels = voxelsOf(target)
    if (voxels == null) {
      out.sendToPlayer(playerId, ChatSMSG(text = "/mm: '$target' is no grid reference, settlement or position", type = ChatType.COMMAND))
      return true
    }

    inbox.offerTeleport(ChunkStreamInbox.Teleport(playerId, voxels.first, voxels.second))

    LOG.info { "Queued /mm '$target' to (${voxels.first},${voxels.second}) for player $playerId" }

    return true
  }

  private fun voxelsOf(target: String): Pair<Long, Long>? {
    RAW_REGEX.matchEntire(target)?.let { match ->
      return match.groupValues[1].toLong() to match.groupValues[2].toLong()
    }

    val config = worldService.config
    val metres = GridRef(config.widthMetres, config.heightMetres).parse(target)
      ?.let { it.eastMetres to it.northMetres }
      // Settlements are placed in worldgen metres.
      ?: areaNames.named(target)?.let { it.x.toDouble() to it.y.toDouble() }
      ?: return null

    return (metres.first / config.voxelSize).roundToLong() to (metres.second / config.voxelSize).roundToLong()
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
    private val CMD_REGEX = Regex("""^/mm\s+(.+)$""")
    private val RAW_REGEX = Regex("""(-?\d+)\s+(-?\d+)""")
  }
}
