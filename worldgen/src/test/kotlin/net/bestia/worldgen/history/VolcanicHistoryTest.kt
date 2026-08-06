package net.bestia.worldgen.history

import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.core.SiteKind
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.geo.VolcanismStage
import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.voxel.ChunkMaterializer
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Eruptions as geography: that they happen at volcanoes, that they sometimes bury a town, and that a town they
 * spared remembers them.
 *
 * ### The sweep, and why a per-seed assertion would be wrong
 *
 * An eruption is rare per vent per tick, and burying a town is rarer still - so on any one seed every element of
 * the census can legitimately be zero. **A rare event working and a dead one look identical on a single seed.**
 * So this asserts on the *sweep total* and prints the per-seed census, which is `ManaHistoryTest`'s shape and is
 * here for the same reason.
 *
 * ### The four claims
 *
 * - **an eruption happens at a vent, not in a town.** The one assertion that catches a regression to the roll
 *   this pass replaced. Before it, the event's `where` was the settlement, so nothing in the chronicle said a
 *   mountain had erupted and `provenanceOf` could not thread a ruin back to one.
 * - **every ash ruin cites the eruption that buried it.** What makes `prune`'s transitive closure able to
 *   explain a mound in the ground, and therefore what a quest could be mined from.
 * - **an eruption can bury nothing, and usually does.** The decoupling that lets `ERUPTION_RATE` be fifty times
 *   what it was without the chronicle filling up with ash. Measured as eruptions far outnumbering ash ruins.
 * - **a standing town has an eruption in its own remembered history.** The property this whole restructuring
 *   exists to create: before it, an eruption *always* destroyed the town that rolled it, so no survivor had one
 *   to remember and there was nothing for an NPC to say. This is the assertion `SettlementLoreService` needs to
 *   have something to find.
 */
class VolcanicHistoryTest {

  @Test
  fun `the volcanoes erupt, bury towns, and the survivors remember`() {
    var eruptions = 0
    var ashRuins = 0
    var remembering = 0
    var seedsWithEruption = 0

    for (seed in SEEDS) {
      val world = worldOf(seed)
      val chronicle = world.world.chronicle

      val erupted = chronicle.events.filter { it.kind == EventKind.ERUPTION }
      val ash = chronicle.sitesOfKind(SiteKind.ASH_RUIN)

      /*
       * A standing town with an eruption inside its own ashfall reach.
       *
       * Counted by **position** rather than from an actor list, and that is not a convenience - an eruption has
       * no settlement actor at all, because it happened to a mountain. Which is exactly the join
       * `SettlementLoreService` will have to make: `SettlementRecord` carries no position, so "what happened near
       * here" means going through the `SETTLEMENT` markers for the coordinates.
       */
      val positions = settlementPositions(world)
      var witnesses = 0
      for (record in chronicle.settlements) {
        if (record.isRuin) continue
        val at = positions[record.index] ?: continue
        if (erupted.any { it.where != null && it.where!!.distanceTo(at) <= ASH_REACH }) witnesses++
      }

      eruptions += erupted.size
      ashRuins += ash.size
      remembering += witnesses
      if (erupted.isNotEmpty()) seedsWithEruption++

      println(
        "seed $seed: ${erupted.size} eruptions, ${ash.size} ash ruins, " +
            "$witnesses standing towns with one in living memory"
      )
    }

    println("totals: $eruptions eruptions, $ashRuins ash ruins, $remembering remembering towns")

    assertTrue(
      seedsWithEruption > 0,
      "no eruption on any of ${SEEDS.size} worlds - the per-vent roll fires never"
    )
    assertTrue(
      ashRuins > 0,
      "$eruptions eruptions across ${SEEDS.size} worlds buried nothing at all. Do NOT raise ERUPTION_RATE to " +
          "fix this - BURIAL_CHANCE is the lever, and the mistake ERUPTION_RATE's KDoc records is exactly the " +
          "one raising it would repeat"
    )
    assertTrue(
      remembering > 0,
      "no standing settlement anywhere in the sweep has an eruption within ash reach. That is the property the " +
          "per-vent roll exists to create, and SettlementLoreService has nothing to find without it"
    )

    // An eruption usually buries nothing, which is what decouples "the mountain erupted" from "a town died".
    // If these ever come close to equal, the burial roll has stopped being a roll.
    assertTrue(
      eruptions > ashRuins * 3,
      "$eruptions eruptions produced $ashRuins ash ruins; an eruption is supposed to be survivable"
    )
  }

  @Test
  fun `an eruption happens at a vent, not in a town`() {
    // The assertion that catches a regression to the per-town roll, and it is stated two ways because either
    // alone is weak: the event carries no settlement actor, *and* its position is at a vent rather than at a town.
    var checked = 0

    for (seed in SEEDS) {
      val world = worldOf(seed)
      val vents = world.world.features.all()
        .filter { it.kind == FeatureKind.VOLCANIC_VENT }
        .filterIsInstance<PointMarker>()
        .map { it.position }
      if (vents.isEmpty()) continue

      for (event in world.world.chronicle.events) {
        if (event.kind != EventKind.ERUPTION) continue
        checked++

        assertTrue(
          event.actors.isEmpty(),
          "seed $seed: an eruption names actors ${event.actors}; it happened to a mountain, not to anybody"
        )

        val where = event.where
        assertTrue(where != null, "seed $seed: an eruption has no position, so nothing can point at it")
        assertTrue(
          vents.any { it.distanceTo(where) < 1e-6 },
          "seed $seed: an eruption at (${where.x.toInt()}, ${where.y.toInt()}) is not at any of the " +
              "${vents.size} vents - the roll has moved back onto the towns"
        )
      }
    }

    assertTrue(checked > 0, "the sweep produced no eruption to check")
  }

  @Test
  fun `every ash ruin cites the eruption that buried it and fits the query margin`() {
    var checked = 0

    for (seed in SEEDS) {
      val world = worldOf(seed)
      val chronicle = world.world.chronicle

      for (site in chronicle.sitesOfKind(SiteKind.ASH_RUIN)) {
        checked++

        // Small enough for the materialiser to find. A site marker is a point, so it is discovered by expanding a
        // chunk's bounds by a fixed margin - a mound reaching past that simply stops at a straight line.
        assertTrue(
          site.radius <= ChunkMaterializer.MARKER_MARGIN,
          "seed $seed: an ash ruin reaches ${site.radius.toInt()} m, past the query margin"
        )

        // And the causal chain, which is what `prune` threads and what a quest would be mined from. The town's
        // own abandonment event has to cite an eruption.
        val record = chronicle.settlements.getOrNull(site.settlement)
        assertTrue(record != null, "seed $seed: an ash ruin names no settlement")

        val obituary = chronicle.eventsOf(net.bestia.worldgen.core.Actor(
          net.bestia.worldgen.core.ActorType.SETTLEMENT, site.settlement
        )).lastOrNull { it.kind == EventKind.SETTLEMENT_BURIED }

        assertTrue(
          obituary != null,
          "seed $seed: settlement ${site.settlement} has an ash ruin but no burial in its own history"
        )
        assertTrue(
          obituary.causes.isNotEmpty(),
          "seed $seed: the burial of settlement ${site.settlement} cites no eruption, so nothing explains it"
        )

        // The cited event is the mountain waking, and it is not the burial itself.
        for (cause in obituary.causes) {
          val cited = chronicle.events.firstOrNull { it.id == cause }
          assertTrue(cited != null, "seed $seed: the burial cites event $cause, which does not exist")
          assertTrue(
            cited.kind == EventKind.ERUPTION && cited.actors.isEmpty(),
            "seed $seed: the burial cites ${cited.kind}, not a mountain erupting"
          )
        }
      }
    }

    println("checked $checked ash ruins across ${SEEDS.size} worlds")
    assertTrue(checked > 0, "the sweep produced no ash ruin to check")
  }

  @Test
  fun `the vent indices history rolls on are dense from zero`() {
    // `resolveEruptions` keys its per-vent roll on this index, so history is a pure function of the seed only
    // while the indices are. A gap wastes a stream; a duplicate makes two volcanoes erupt in lockstep forever.
    for (seed in SEEDS) {
      val indices = worldOf(seed).world.features.all()
        .filter { it.kind == FeatureKind.VOLCANIC_VENT }
        .filterIsInstance<PointMarker>()
        .map { it.attribute(VolcanismStage.CHANNEL_INDEX).toInt() }
        .sorted()

      assertTrue(
        indices == indices.indices.toList(),
        "seed $seed: vent indices are $indices, which is not dense from zero"
      )
    }
  }

  /** Settlement index to position, through the markers - the chronicle's records carry no coordinates. */
  private fun settlementPositions(world: GeneratedWorld): Map<Int, net.bestia.worldgen.vector.Vec2d> =
    world.world.features.all()
      .filter { it.kind == FeatureKind.SETTLEMENT }
      .filterIsInstance<PointMarker>()
      .associate { it.attribute(SettlementChannels.INDEX).toInt() to it.position }

  private val built = HashMap<Long, GeneratedWorld>()

  /** Built once per seed and shared, since four of the tests walk the same worlds. */
  private fun worldOf(seed: Long) = built.getOrPut(seed) {
    StandardWorld.build(WorldConfig(seed = seed, widthCells = CELLS, heightCells = CELLS))
  }

  private companion object {
    /**
     * 256 cells, and not `ManaHistoryTest`'s 128.
     *
     * The claim needs a world that reliably holds a convergent boundary *and* a town within ash reach of a vent,
     * which is `SpecialSitesTest`'s reason for the same size. On 128 the towns and the volcanoes are both there
     * and are mostly not near each other, so the burial census reads zero for a reason that has nothing to do
     * with whether burying works.
     */
    const val CELLS = 256

    /** `HistorySim.ASH_REACH`, duplicated because it is `private`. If it moves there, this reads the wrong reach. */
    const val ASH_REACH = 18_000.0

    val SEEDS = listOf(1L, 3L, 7L, 42L)
  }
}
