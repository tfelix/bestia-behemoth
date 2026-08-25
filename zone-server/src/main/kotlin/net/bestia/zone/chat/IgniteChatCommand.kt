package net.bestia.zone.chat

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.fire.GroundFireService
import net.bestia.zone.world.stream.ChunkStreamConfig
import org.springframework.stereotype.Component

/**
 * `/ignite <x> <y> [radius]` - sets light to the grass. The development trigger for spreading fire.
 *
 * A chat command for `CarveChatCommand`'s reasons, which apply here word for word: the framework already
 * carries the authority check, the help text and the error reply, so a dev-only capability costs no wire
 * message, no generated C# and no client code at all.
 *
 * It exists so the whole chain - fuel, the automaton, the scorch store, the overlay fan-out and the client's
 * rendering of it - can be driven by hand and watched, without needing a caster, a skill and a target. `Ember`
 * lights fires in the game; this is how you find out whether the fire *works*.
 *
 * No `z`, unlike `/carve`: a fire is on the surface, so the only sensible height is the one the ground is at.
 *
 * ### It reports queued, not lit
 *
 * Chat is dispatched on a Netty worker thread, so this goes through `GroundFireService.requestIgnition` and
 * starts on the next tick. Whether anything actually caught depends on the ground, and this cannot know - so
 * it says it asked. A `true` here means the command parsed, not that a fire is burning.
 */
@Component
class IgniteChatCommand(
  private val fire: GroundFireService,
  private val settings: ChunkStreamConfig
) : ChatCommand() {

  /**
   * `TERRAIN`, the same authority `/carve` needs.
   *
   * A fire permanently changes the ground and can be started anywhere in the world from a chat box, so it
   * belongs with the other capability that edits terrain rather than with the ones that only inspect it.
   */
  override val requiredAuthority: Authority = Authority.TERRAIN

  override fun getHelpText() =
    "/ignite <X> <Y> [RADIUS] - Sets light to the grass at a world position. RADIUS is in tiles and defaults " +
        "to $DEFAULT_RADIUS. Nothing happens on ground that will not burn - stone, sand, snow, a bog, or " +
        "anywhere it is raining hard."

  override fun isMatch(cmdText: String) = CMD_REGEX.matches(cmdText.trim())

  override fun execute(playerId: Long, cmdText: String): Boolean {
    // Gated on the same flag as `/carve`, and for the same reason: both write a lasting change to the world
    // from a chat box, so a server that refuses one should refuse the other.
    if (!settings.allowDebugEdits) {
      LOG.info { "Refused /ignite from $playerId: chunk-stream.allow-debug-edits is off" }
      return false
    }

    val match = CMD_REGEX.find(cmdText.trim()) ?: return false

    val x = match.groupValues[1].toLong()
    val y = match.groupValues[2].toLong()
    val radius = match.groupValues[3].takeIf { it.isNotEmpty() }?.toLongOrNull() ?: DEFAULT_RADIUS

    if (radius > MAX_RADIUS) {
      LOG.warn { "Refused /ignite from $playerId: radius $radius is over the $MAX_RADIUS cap" }
      return false
    }

    fire.requestIgnition(
      centre = Vec3L(x, y, 0),
      radiusTiles = radius,
      // The caster is whoever typed it, so the fire's damage is attributed to them and `hitsCaster` means
      // something. Standing in your own test fire should hurt.
      casterId = playerId,
      skillId = 0L,
      skillLevel = 1
    )

    LOG.info { "Queued /ignite ($x,$y) r=$radius for player $playerId" }

    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /** Two tiles, so an ignition is visibly a patch rather than one cell that may or may not have caught. */
    const val DEFAULT_RADIUS = 2L

    /**
     * A cap, because the ignition loop is `(2r+1)²` fuel lookups **on the tick thread** before the automaton
     * takes over. Sixteen is a thousand-odd lookups, which is fine; a typo of 16000 would not be.
     */
    const val MAX_RADIUS = 16L

    private val CMD_REGEX = Regex("""^/ignite\s+(-?\d+)\s+(-?\d+)(?:\s+(\d+))?$""")
  }
}
