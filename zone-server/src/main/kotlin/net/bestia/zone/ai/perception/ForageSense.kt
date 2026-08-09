package net.bestia.zone.ai.perception

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.domain.bestia.VegetationMemory
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Component

/**
 * Notices grazeable ground under a creature's feet and remembers it, which is the writer
 * `BestiaDomain.KNOWN_VEGETATION` never had.
 *
 * Without this the whole foraging half of the domain was inert. `WalkToVegetation` and `EatVegetation` both
 * ground against remembered spots, nothing ever put a spot in, so both always grounded to nothing — a hungry
 * creature selected `EatVegetation`, failed to plan, and went back to ambling. The behaviour existed on paper
 * and could not run.
 *
 * ### Why discovery is walking over it, rather than seeing it
 *
 * A sight-radius sweep would mean sampling the biome across a disc of tiles per creature per sweep, and the
 * answer would be "grass" for nearly every tile of it — [BiomeForageGround] describes country, not clumps.
 * One sample under the feet costs a single lookup, spreads discovery over the ground a creature actually
 * covers, and produces the behaviour that reads correctly anyway: an animal wanders, finds it is standing on
 * grass, and remembers the spot.
 *
 * Spots land on the *team* board, because the key is `MemoryScope.TEAM` and [SenseContext.remember] routes by
 * scope — so one animal's find is its pack's, and eating one already cascades the removal back out the same
 * way. [MIN_SPACING] and [MAX_REMEMBERED] are what keep that from turning into a faction-wide list of every
 * tile anything ever stood on.
 */
@Component
class ForageSense(
  private val ground: ForageGround,
) : Sense {

  override val name = "forage"

  /**
   * Slower than sight on purpose. Where the grass is changes on the timescale of a world, not of a creature's
   * glance, and a creature cannot cross [MIN_SPACING] tiles in less than this anyway — so a faster cadence
   * would buy nothing but biome lookups.
   */
  override val intervalSeconds = 2f

  override fun sense(context: SenseContext) {
    val here = context.position
    val known = context.recall(BestiaDomain.KNOWN_VEGETATION).orEmpty()

    if (!worthRemembering(known, here)) return
    if (!ground.isGrazeable(here)) return

    context.remember(BestiaDomain.KNOWN_VEGETATION, known + VegetationMemory(here, System.currentTimeMillis()))
  }

  /**
   * The cheap tests, run before the biome lookup so the common case — an animal that has been standing on
   * ground it already knows about — costs a couple of distance comparisons and no sampling at all.
   */
  private fun worthRemembering(known: List<VegetationMemory>, here: Vec3L): Boolean =
    known.size < MAX_REMEMBERED && known.none { it.position.distance(here) <= MIN_SPACING }

  companion object {
    /**
     * How far apart remembered spots are kept, in tiles.
     *
     * Comfortably wider than `BestiaDomain.ARRIVAL_RADIUS`, so a creature standing on a spot it just grazed
     * out does not immediately rediscover the same patch under a new position — the eating effect drops the
     * spot to model it being fed off for a while, and re-adding it a tick later would undo that.
     */
    private const val MIN_SPACING = 6L

    /**
     * Cap on the shared map, per pack.
     *
     * It is a cap on *memory*, not on how much grass exists: entries fall out on the blackboard's ordinary
     * TTL as well, so a pack that moves on forgets the meadow it left behind rather than holding a growing
     * list of everywhere it has ever been.
     */
    private const val MAX_REMEMBERED = 48
  }
}
