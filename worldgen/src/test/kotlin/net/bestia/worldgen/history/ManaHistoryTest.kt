package net.bestia.worldgen.history

import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.SiteKind
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.mana.CorruptionStage
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.Invariants
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.voxel.ChunkMaterializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The history half of the mana subsystem: a star, four consequences, and a place on the map.
 *
 * `every wound stands in ground the corruption reached` exists because it was **false on 40% of wounds** in the
 * first version and nothing in the module noticed. See `CorruptionParams.woundRange`.
 *
 * `the exposure field is a neighbourhood maximum` exists because the *first* version of it asserted something
 * false. It claimed a point sample would put so few towns at risk that the blight would never fire, and the
 * measured figures are 62 sites against 79 - a real widening, not the difference between working and dead. The
 * test now asserts the property the field actually has. Worth remembering when reading the rest of this file:
 * the argument for a design and the test for it are not the same thing, and only one of them gets checked.
 */
class ManaHistoryTest {

  private val seeds = listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L)

  private fun world(seed: Long) =
    StandardWorld.build(WorldConfig(seed = seed, widthCells = 128, heightCells = 128))

  private fun corruption(world: GeneratedWorld) =
    world.world.layers.require<FloatLayer>(LayerId.CORRUPTION)

  private fun mana(world: GeneratedWorld) =
    world.world.layers.require<FloatLayer>(LayerId.MANA_DENSITY)

  @Test
  fun `every wound stands in ground the corruption reached`() {
    // Both halves matter. A wound on clean grass is a hole in the world with nothing wrong with the land around
    // it, which makes the corrupted-land endgame exist on some seeds and not others; a wound under water is one
    // nobody can walk to. The first was true six times in fifteen before `CorruptionStage.woundLift`.
    var total = 0

    for (seed in seeds) {
      val built = world(seed)
      val corruption = corruption(built)
      val water = built.world.layers.require<FloatLayer>(LayerId.WATER_LEVEL)
      val elevation = built.world.layers.require<FloatLayer>(LayerId.ELEVATION)

      for (wound in built.world.chronicle.sitesOfKind(SiteKind.WOUND)) {
        total++
        val x = wound.position.x
        val y = wound.position.y

        assertTrue(
          corruption.sampleBilinear(x, y) >= CorruptionStage.CORRUPTED,
          "seed $seed: the wound at (${x.toInt()}, ${y.toInt()}) stands in corruption " +
              "${corruption.sampleBilinear(x, y)}"
        )
        assertTrue(
          water.sampleBilinear(x, y).isNaN(),
          "seed $seed: the wound at (${x.toInt()}, ${y.toInt()}) is under standing water"
        )
        assertTrue(
          elevation.sampleBilinear(x, y) > built.config.seaLevel,
          "seed $seed: the wound at (${x.toInt()}, ${y.toInt()}) is below sea level"
        )

        // The one marker in the world that comes anywhere near the chunk query margin. Past it a site is absent
        // from every chunk further away than the margin and materialises with a straight edge down one side.
        assertTrue(
          wound.radius <= ChunkMaterializer.MARKER_MARGIN,
          "seed $seed: a wound reaches ${wound.radius} m, past the ${ChunkMaterializer.MARKER_MARGIN} m margin"
        )
      }
    }

    println("wounds over ${seeds.size} seeds: $total")
    assertTrue(total >= seeds.size, "only $total wounds over ${seeds.size} seeds; the star is not falling")
  }

  @Test
  fun `the exposure field is a neighbourhood maximum`() {
    // Two properties, both of which a "simplification" to `mana.sampleBilinear(town)` would break, and both
    // stated at the strength the measurement supports rather than at the strength the design argument wanted.
    //
    //   1. It dominates the point sample everywhere. That is what makes `blightMana` readable as a percentile:
    //      a town's exposure is never *less* than the mana under its own feet.
    //   2. It is strictly greater at a substantial minority of sites - the towns near a province rather than in
    //      one, which are the ones with a story. Measured at 79 at-risk sites against 62.
    var proximityAtRisk = 0
    var pointAtRisk = 0
    var settlements = 0
    var raised = 0

    for (seed in seeds) {
      val built = world(seed)
      val field = mana(built)
      val threshold = built.params.history.blightMana
      val range = built.params.history.blightRange
      val metres = field.region.resolution.metresPerCell
      val reach = Math.ceil(range / metres).toInt()

      for (marker in built.world.features.all()
        .filter { it.kind == FeatureKind.SETTLEMENT }
        .filterIsInstance<PointMarker>()) {
        settlements++

        val point = field.sampleBilinear(marker.position.x, marker.position.y)
        if (point >= threshold) pointAtRisk++

        var worst = 0.0
        val cellX = (marker.position.x / metres).toInt()
        val cellY = (marker.position.y / metres).toInt()
        for (dy in -reach..reach) {
          for (dx in -reach..reach) {
            val distance = Math.sqrt((dx * dx + dy * dy).toDouble()) * metres
            if (distance > range) continue
            val value = field[cellX + dx, cellY + dy].toDouble()
            if (!value.isNaN()) worst = maxOf(worst, value * (1.0 - distance / range))
          }
        }
        if (worst >= threshold) proximityAtRisk++

        // Property 1, per site rather than in aggregate: a maximum over a disc that includes the centre cannot
        // come back below the centre's own value. A taper applied to the *centre* term - the obvious slip when
        // writing this - would break it, and only at the sites where the centre is the maximum.
        assertTrue(
          worst >= point - TOLERANCE,
          "exposure $worst is below the point sample $point at (${marker.position.x.toInt()}, " +
              "${marker.position.y.toInt()}) - the neighbourhood maximum is not including its own centre"
        )
        if (worst > point + MEANINGFUL) raised++
      }
    }

    println(
      "of $settlements settlement sites: $proximityAtRisk at risk by the neighbourhood maximum, " +
          "$pointAtRisk by a point sample; $raised sites read materially higher"
    )

    assertTrue(proximityAtRisk > 0, "no settlement anywhere is exposed to the mana; nothing can ever blight")
    // Property 2. A point sample would score zero here, and so would a range small enough to fit inside one
    // raster cell - which is the other way this quietly stops being a neighbourhood.
    assertTrue(
      raised > settlements / 5,
      "only $raised of $settlements sites read higher than their own cell; HistoryStage.manaField is behaving " +
          "like the point sample it is not supposed to be"
    )
  }

  @Test
  fun `all four consequences of the star happen somewhere`() {
    // The counter that makes habit 6 impossible here. Each of the four can be zero on one world - a seed can go
    // a thousand years without a town giving up - so this asserts against the *sweep* total and prints the
    // per-seed spread, which is the only way to tell a rare event working from a dead one.
    val labels = listOf("wounds", "blights", "wards", "forsaken", "seers lost")
    val totals = IntArray(labels.size)

    for (seed in seeds) {
      val counts = Invariants.manaHistoryCensus(world(seed))
      println("seed $seed: " + labels.indices.joinToString(", ") { "${labels[it]} ${counts[it]}" })
      for (i in labels.indices) totals[i] += counts[i]
    }

    for (i in labels.indices) {
      assertTrue(
        totals[i] > 0,
        "${labels[i]} came to zero over all ${seeds.size} seeds, which is a subsystem that never fires"
      )
    }
  }

  @Test
  fun `every consequence names the star as its cause`() {
    // What makes a blighted province something a player can be *told about*. `Chronicle.provenanceOf` and the
    // causal closure in `HistorySim.prune` both walk `causes`, so a dangling or missing link is not a cosmetic
    // problem - it is a thread that cannot be followed back to why.
    for (seed in seeds) {
      val chronicle = world(seed).world.chronicle
      val byId = chronicle.events.associateBy { it.id }
      val fell = chronicle.events.filter { it.kind == EventKind.STAR_FELL }.map { it.id }.toSet()
      if (fell.isEmpty()) continue

      val consequences = chronicle.events.filter {
        it.kind == EventKind.BLIGHT_SPREAD || it.kind == EventKind.WARD_RAISED ||
            it.kind == EventKind.SEER_VANISHED
      }
      assertTrue(consequences.isNotEmpty(), "seed $seed: a star fell and nothing followed from it")

      for (event in consequences) {
        assertTrue(
          event.causes.any { it in fell },
          "seed $seed: ${event.kind} at year ${event.year} cites ${event.causes}, none of them the fall"
        )
        // Pruning keeps anything a survivor cites, so every id here has to resolve. A hole in the chain is
        // worse than a shorter chain - see `HistorySim.prune`.
        for (cause in event.causes) {
          assertTrue(cause in byId, "seed $seed: ${event.kind} cites event $cause, which was pruned away")
        }
      }
    }
  }

  @Test
  fun `a warded town is never forsaken`() {
    // The observable difference a ward makes, and the whole reason wards exist rather than being flavour: a
    // town that raised them stops accruing towards being given up. Without the clause in `resolveMana` that
    // does this, a ward would delay an abandonment rather than prevent one and every town in a blighted
    // province would empty eventually - a subsystem with no effect anybody could see.
    var warded = 0
    var forsaken = 0

    for (seed in seeds) {
      val chronicle = world(seed).world.chronicle

      val wardedIndices = chronicle.events
        .filter { it.kind == EventKind.WARD_RAISED }
        .flatMap { event -> event.actors.filter { it.type == net.bestia.worldgen.core.ActorType.SETTLEMENT } }
        .map { it.index }
        .toSet()
      warded += wardedIndices.size

      for (record in chronicle.settlements) {
        if (record.ruinCause != EventKind.SETTLEMENT_FORSAKEN) continue
        forsaken++
        assertEquals(
          false, record.index in wardedIndices,
          "seed $seed: settlement ${record.index} raised wards and was still given up to the blight"
        )
      }
    }

    println("$warded towns warded, $forsaken forsaken over ${seeds.size} seeds")
    assertTrue(warded > 0 && forsaken > 0, "one side of the comparison is empty: $warded warded, $forsaken lost")
  }

  private companion object {
    /** The point sample is bilinear and the neighbourhood reads cells, so they differ in the last bit. */
    const val TOLERANCE = 1e-6

    /** Enough of a difference to be the neighbourhood talking rather than interpolation. */
    const val MEANINGFUL = 0.02
  }
}
