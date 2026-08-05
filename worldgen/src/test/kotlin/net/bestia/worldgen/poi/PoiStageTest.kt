package net.bestia.worldgen.poi

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Points of interest, measured over a sweep of seeds rather than on one world.
 *
 * ### Why a sweep, when every other stage's test uses one world
 *
 * Because the claim being tested *is* a claim about the distribution over worlds. [PoiKind.chance] says "forty
 * five worlds in a hundred hold a lost grave", and a single world can only ever say "this one does" or "this one
 * does not" - neither of which is evidence about the number. So the unit here is a run of worlds, and the
 * measurements below are counts across it.
 *
 * The worlds are reduced to a list of [Placed] as they are built and then dropped, rather than held. Twenty-odd
 * whole worlds resident at once is a couple of hundred megabytes of rasters to answer a question about a handful
 * of points.
 *
 * ### What this is really guarding, and it is not the roll
 *
 * The roll is `GenRng.hashUnit(...) < chance`, which is not the sort of thing that goes quietly wrong. What goes
 * quietly wrong is **placement**: an entry whose biome filter names a combination the classifier does not
 * produce, or whose clearances no candidate can satisfy, passes its roll and then finds nowhere to stand. The
 * result is a catalogue entry nobody ever sees, on any seed, with nothing failing.
 *
 * That is not a hypothetical - it is this module's most-repeated failure. `AetheriteScatter`'s KDoc records the
 * first version of that scatter yielding "zero to one site per world, usually under an ice sheet", and
 * `SpecialSitesTest`'s records this whole subsystem once producing zero sites on every world. Hence
 * [`every catalogue entry is reachable`], which is the load-bearing test in this file, asserted per entry and
 * never aggregated - `SpecialSitesTest`'s rule that a total is what lets one dead kind hide behind three healthy
 * ones.
 *
 * ### The measured table, and why the bounds are loose
 *
 * Over [SEEDS] worlds from [FIRST_SEED] at [CELLS] cells, worlds holding each entry against the number its
 * chance predicts:
 *
 * | entry | chance | predicted | measured | ratio |
 * |---|---|---|---|---|
 * | `LOST_GRAVE` | 0.45 | 10.8 | 8 | 0.74 |
 * | `STANDING_STONES` | 0.60 | 14.4 | 11 | 0.76 |
 * | `BROKEN_OBELISK` | 0.35 | 8.4 | 9 | 1.07 |
 * | `WAYSTONE` | 0.70 | 16.8 | 14 | 0.83 |
 * | `PETRIFIED_TREE` | 0.30 | 7.2 | 7 | 0.97 |
 * | `SUNKEN_IDOL` | 0.25 | 6.0 | 7 | 1.17 |
 * | **total** | | **63.6** | **56** | **0.88** |
 *
 * The worst entry is at 0.74 of its prediction and two are above theirs, so nothing here is starving. The
 * aggregate is 1.4 standard deviations low - the total's own sd over 144 Bernoulli trials is 5.6 - which is
 * ordinary sampling noise rather than a systematic loss, and the two entries above prediction are what says so:
 * a placement failure can only ever push a count *down*.
 *
 * The lower bound is nonetheless a *share* of the prediction rather than a confidence interval, because a second
 * shortfall stacks on the noise and is not a bug: an entry can pass its roll on a world that has nowhere to put
 * it. `LOST_GRAVE` is the entry most exposed to that - four of its five biomes are the ones a 192 km world can
 * legitimately lack entirely - and a bound tight enough to call that a failure would fail on the ground rather
 * than on the code.
 *
 * The upper bound is the tighter of the two and worth having for a reason the lower one cannot cover: nothing may
 * place a landmark more often than its roll allows, so an entry well over its prediction means the chance is not
 * being read at all.
 */
class PoiStageTest {

  // --- They exist -----------------------------------------------------------------------------------

  @Test
  fun `every catalogue entry is reachable`() {
    for (kind in PoiKind.entries) {
      assertTrue(
        worldsHolding(kind) > 0,
        "${kind.label} appeared on none of $SEEDS worlds. Either its biome filter names ground no world has, " +
            "or its clearances leave nowhere to stand. Measured: ${census()}"
      )
    }
  }

  @Test
  fun `worlds hold each entry about as often as its chance says`() {
    for (kind in PoiKind.entries) {
      val held = worldsHolding(kind)
      val expected = kind.chance * SEEDS

      assertTrue(
        held >= expected * MIN_SHARE_OF_EXPECTED,
        "${kind.label} was placed on $held of $SEEDS worlds against a predicted ${expected.roundToInt()}. " +
            "A shortfall this large is a filter most worlds cannot satisfy, not sampling noise. " +
            "Measured: ${census()}"
      )
      assertTrue(
        held <= expected + BINOMIAL_SLACK,
        "${kind.label} was placed on $held of $SEEDS worlds, more than its chance of ${kind.chance} allows - " +
            "nothing may place a landmark more often than the roll. Measured: ${census()}"
      )
    }
  }

  // --- They are one thing, in the right place -------------------------------------------------------

  @Test
  fun `a world holds at most one of each entry`() {
    for ((seed, placed) in worlds) {
      for ((kind, group) in placed.groupBy { it.kind }) {
        assertEquals(
          1,
          group.size,
          "seed $seed holds ${group.size} of ${kind.label}; one roll per world must mean one landmark"
        )
      }
    }
  }

  @Test
  fun `every landmark stands in a biome its entry allows`() {
    for ((seed, placed) in worlds) {
      for (poi in placed) {
        assertTrue(
          poi.kind.allows(poi.biome),
          "seed $seed put a ${poi.kind.label} on ${poi.biome.label}, which is not in its filter " +
              "${poi.kind.biomes.map { it.label }}"
        )
      }
    }
  }

  @Test
  fun `every landmark stands out of the water`() {
    for ((seed, placed) in worlds) {
      for (poi in placed) {
        assertTrue(
          !poi.biome.isWater,
          "seed $seed put a ${poi.kind.label} on ${poi.biome.label}"
        )
      }
    }
  }

  // --- The same world twice is the same world -------------------------------------------------------

  @Test
  fun `placement is a function of the seed`() {
    val once = placedIn(REPEAT_SEED)
    val twice = placedIn(REPEAT_SEED)

    assertEquals(
      once.map { "${it.kind.name}@${it.x},${it.y}" },
      twice.map { "${it.kind.name}@${it.x},${it.y}" },
      "two builds of seed $REPEAT_SEED disagree about where its landmarks are"
    )
  }

  private companion object {

    /**
     * Worlds in the sweep.
     *
     * Enough that a 0.25-chance entry is all but certain to appear - `0.75^24` is under one in a thousand - and
     * few enough that the sweep is a few seconds rather than a minute. The lower bound in
     * [`worlds hold each entry about as often as its chance says`] is what this number really pays for: at
     * twenty-four samples the binomial standard deviation is about 2.4, so a shortfall of half is well outside
     * the noise and a shortfall of a fifth would not be.
     */
    const val SEEDS = 24

    const val FIRST_SEED = 1L

    /**
     * Cells per axis, so a 192 km world.
     *
     * Larger than `HistoryStageTest`'s 160 and smaller than `SpecialSitesTest`'s 256, and the reason is biome
     * diversity rather than site count: three of the six entries name a biome that only exists at a particular
     * latitude, and a world too small to have a climate band has no chance of holding them. At 192 all six
     * entries clear their bounds with room to spare - see the table in the class KDoc - which is what says this
     * size is enough rather than merely affordable.
     */
    const val CELLS = 192

    /** A seed built twice, for the determinism check. Outside the sweep's range so it is genuinely a fresh build. */
    const val REPEAT_SEED = 4_242L

    /**
     * How far below its prediction an entry may fall before it is a filter nobody can satisfy.
     *
     * A share rather than a confidence interval, because two independent shortfalls stack here: binomial noise
     * at twenty-four samples, and worlds that pass the roll and legitimately have nowhere to put the thing. The
     * worst entry measures at 0.74 of prediction, so this leaves a wide margin against noise while still catching
     * the failure it exists for, which is an entry at zero or near it.
     */
    const val MIN_SHARE_OF_EXPECTED = 0.45

    /** Roughly two binomial standard deviations at these counts, rounded up. See the class KDoc. */
    const val BINOMIAL_SLACK = 5.0

    /** One landmark on one world, with everything about it the tests need after the world is dropped. */
    class Placed(val kind: PoiKind, val x: Double, val y: Double, val biome: Biome)

    /** The sweep, reduced as it is built. See the class KDoc for why the worlds are not kept. */
    val worlds: List<Pair<Long, List<Placed>>> =
      (0 until SEEDS).map { i -> (FIRST_SEED + i).let { it to placedIn(it) } }

    fun placedIn(seed: Long): List<Placed> {
      val world = StandardWorld.build(StandardWorld.demoConfig(seed).copy(widthCells = CELLS, heightCells = CELLS))
      return read(world)
    }

    fun read(world: GeneratedWorld): List<Placed> {
      val biome = world.world.layers.require<IntLayer>(LayerId.BIOME)
      val metres = world.config.baseResolution.metresPerCell

      return world.world.features.all()
        .filter { it.kind == FeatureKind.POI }
        .filterIsInstance<PointMarker>()
        .map { marker ->
          Placed(
            kind = PoiKind.entries[marker.attribute(PoiChannels.KIND).toInt()],
            x = marker.position.x,
            y = marker.position.y,
            // Nearest, never interpolated: a biome ordinal has no midpoint. The same cell the stage read.
            biome = Biome.entries[biome[(marker.position.x / metres).toInt(), (marker.position.y / metres).toInt()]]
          )
        }
        .sortedBy { it.kind.ordinal }
    }

    fun worldsHolding(kind: PoiKind): Int = worlds.count { (_, placed) -> placed.any { it.kind == kind } }

    /** The whole table, for a failure message. A count on its own never says which entry starved. */
    fun census(): String =
      PoiKind.entries.joinToString(", ") { "${it.name}=${worldsHolding(it)}/$SEEDS@${it.chance}" }
  }
}
