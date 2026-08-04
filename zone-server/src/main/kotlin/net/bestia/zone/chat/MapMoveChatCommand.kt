package net.bestia.zone.chat

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.zone.world.stream.ChunkStreamInbox
import org.springframework.stereotype.Component

/**
 * `/mm <x> <y>` - moves the caller's active entity across the map, landing them on the ground.
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
  private val inbox: ChunkStreamInbox
) : ChatCommand() {

  /**
   * Note that [Authority.MAP_MOVE] is currently granted to `Role.USER`, so this is open to every player.
   * That matches how `ITEM` is granted today and is fine while nothing is live, but moving it to the GM roles
   * is a one-line change in `Role` when it stops being fine.
   */
  override val requiredAuthority: Authority = Authority.MAP_MOVE

  override fun getHelpText() =
    "/mm <X> <Y> - Moves you to a map position, standing on the ground there. The world is 128 km across, so " +
        "the middle is around 64000 64000."

  override fun isMatch(cmdText: String) = CMD_REGEX.matches(cmdText.trim())

  override fun execute(playerId: Long, cmdText: String): Boolean {
    val match = CMD_REGEX.find(cmdText.trim()) ?: return false

    val x = match.groupValues[1].toLong()
    val y = match.groupValues[2].toLong()

    inbox.offerTeleport(ChunkStreamInbox.Teleport(playerId, x, y))

    LOG.info { "Queued /mm to ($x,$y) for player $playerId" }

    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
    private val CMD_REGEX = Regex("""^/mm\s+(-?\d+)\s+(-?\d+)$""")
  }
}
