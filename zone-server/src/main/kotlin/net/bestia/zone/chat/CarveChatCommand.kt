package net.bestia.zone.chat

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.worldgen.voxel.CarveBrush
import net.bestia.zone.world.stream.ChunkStreamConfig
import net.bestia.zone.world.stream.ChunkStreamInbox
import org.springframework.stereotype.Component

/**
 * `/carve <x> <y> <z> [radius]` - removes a sphere of rock. The development trigger for terrain mining.
 *
 * Nothing in the game mines yet, which would leave the whole change-broadcast path built and never executed.
 * This exists so the chain from a brush through the delta, the derived-structure invalidation, the revision
 * bump and the patch fan-out can be driven by hand from the cli-client or the chat box and watched end to end.
 *
 * A chat command rather than its own message, which its predecessor briefly was: the command framework already
 * carries the authority check, the help text and the error reply, and this way a dev-only capability costs no
 * wire message, no generated C# and no client code at all.
 *
 * The carve is queued rather than applied. Chat is dispatched on a Netty worker thread and the chunk store has
 * one owning thread - see [ChunkStreamInbox].
 *
 * ### It takes a radius, and there is no way to ask for one voxel
 *
 * This replaced `/setblock <x> <y> <z> <BLOCK>`, and the shape of the replacement is the interesting part. There
 * is no building system and there never will be, so there is nothing for a block argument to say - every
 * mutation is a removal. And a *single-voxel* removal is not offered either, because the client cannot draw one:
 * surface nets over an eight-cell corner average cannot represent a void thinner than two voxels, so a lone
 * carved voxel renders as nothing at all while the server records air. [CarveBrush.MIN_RADIUS] is the floor,
 * and the brush refuses anything under it rather than leaving it to a caller to remember.
 */
@Component
class CarveChatCommand(
  private val inbox: ChunkStreamInbox,
  private val settings: ChunkStreamConfig
) : ChatCommand() {

  override val requiredAuthority: Authority = Authority.TERRAIN

  override fun getHelpText() =
    "/carve <X> <Y> <Z> [RADIUS] - Removes a sphere of rock. Z is the global vertical index, 0 is sea level. " +
        "RADIUS is in voxels and defaults to ${DEFAULT_RADIUS}; anything under ${CarveBrush.MIN_RADIUS} is " +
        "refused, because the client cannot render a bore that small."

  override fun isMatch(cmdText: String) = CMD_REGEX.matches(cmdText.trim())

  override fun execute(playerId: Long, cmdText: String): Boolean {
    if (!settings.allowDebugEdits) {
      LOG.info { "Refused /carve from $playerId: chunk-stream.allow-debug-edits is off" }
      return false
    }

    val match = CMD_REGEX.find(cmdText.trim()) ?: return false

    val x = match.groupValues[1].toLong()
    val y = match.groupValues[2].toLong()
    val z = match.groupValues[3].toLong()
    val radius = match.groupValues[4].takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: DEFAULT_RADIUS

    if (radius < CarveBrush.MIN_RADIUS) {
      LOG.warn {
        "Refused /carve from $playerId: radius $radius is below ${CarveBrush.MIN_RADIUS}, which would " +
            "remove rock the client cannot draw"
      }
      return false
    }

    inbox.offerCarve(ChunkStreamInbox.Carve(playerId, x, y, z, radius))

    LOG.info { "Queued /carve ($x,$y,$z) r=$radius for player $playerId" }

    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /** The minimum, because a bigger default would make the floor harder to notice when testing it. */
    const val DEFAULT_RADIUS = CarveBrush.MIN_RADIUS

    private val CMD_REGEX =
      Regex("""^/carve\s+(-?\d+)\s+(-?\d+)\s+(-?\d+)(?:\s+(\d+(?:\.\d+)?))?$""")
  }
}
