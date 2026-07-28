package net.bestia.zone.chat

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.worldgen.voxel.BlockType
import net.bestia.zone.world.stream.ChunkStreamConfig
import net.bestia.zone.world.stream.ChunkStreamInbox
import org.springframework.stereotype.Component

/**
 * `/setblock <x> <y> <z> <BLOCK>` - sets one voxel. The development trigger for terrain editing.
 *
 * Nothing in the game edits terrain yet, which would leave the whole change-broadcast path built and never
 * executed. This exists so the chain from an edit through the delta, the derived-structure invalidation, the
 * revision bump and the patch fan-out can be driven by hand from the cli-client or the chat box and watched
 * end to end.
 *
 * A chat command rather than its own message, which it briefly was: the command framework already carries the
 * authority check, the help text and the error reply, and this way a dev-only capability costs no wire
 * message, no generated C# and no client code at all.
 *
 * The edit is queued rather than applied. Chat is dispatched on a Netty worker thread and the chunk store has
 * one owning thread - see [ChunkStreamInbox].
 */
@Component
class SetBlockChatCommand(
  private val inbox: ChunkStreamInbox,
  private val settings: ChunkStreamConfig
) : ChatCommand() {

  override val requiredAuthority: Authority = Authority.TERRAIN

  override fun getHelpText() =
    "/setblock <X> <Y> <Z> <BLOCK> - Sets one voxel. Z is the global vertical index, 0 is sea level. " +
        "BLOCK is a name from the block palette, e.g. AIR, GRASS, MASONRY."

  override fun isMatch(cmdText: String) = CMD_REGEX.matches(cmdText.trim())

  override fun execute(playerId: Long, cmdText: String): Boolean {
    if (!settings.allowDebugEdits) {
      LOG.info { "Refused /setblock from $playerId: chunk-stream.allow-debug-edits is off" }
      return false
    }

    val match = CMD_REGEX.find(cmdText.trim()) ?: return false

    val x = match.groupValues[1].toLong()
    val y = match.groupValues[2].toLong()
    val z = match.groupValues[3].toLong()
    val name = match.groupValues[4].uppercase()

    val block = BlockType.entries.firstOrNull { it.name == name }
    if (block == null) {
      LOG.warn { "Refused /setblock from $playerId: '$name' is not a block type" }
      return false
    }

    inbox.offerEdit(ChunkStreamInbox.Edit(playerId, x, y, z, block.id))

    LOG.info { "Queued /setblock ($x,$y,$z) to $block for player $playerId" }

    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
    private val CMD_REGEX = Regex("""^/setblock\s+(-?\d+)\s+(-?\d+)\s+(-?\d+)\s+(\w+)$""")
  }
}
