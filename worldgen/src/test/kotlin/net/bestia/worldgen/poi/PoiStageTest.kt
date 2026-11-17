package net.bestia.worldgen.poi

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.pipeline.TestWorlds
import net.bestia.worldgen.pipeline.WorldParams
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Points of interest, measured over a sweep of seeds rather than on one world, because [PoiKind.chance] is a
 * claim about the distribution over worlds.
 *
 * What goes quietly wrong is placement, not the roll: an entry whose filters no world can satisfy passes its roll
 * and finds nowhere to stand. For an optional entry that is silent, so [`every catalogue entry is reachable`] is
 * the load-bearing test here, asserted per entry and never aggregated.
 */
class PoiStageTest {

  // --- They exist -----------------------------------------------------------------------------------

  @Test
  fun `every catalogue entry is reachable`() {
    for (kind in PoiKind.entries) {
      assertTrue(
        copiesPlaced(kind) > 0,
        "${kind.label} appeared on none of $SEEDS worlds. Either its biome filter names ground no world has, " +
          "or its clearances leave nowhere to stand. Measured: ${census()}"
      )
    }
  }

  @Test
  fun `every required entry stands on every world`() {
    for ((seed, placed) in worlds) {
      for (kind in PoiKind.entries.filter { it.isRequired }) {
        assertEquals(
          kind.count,
          placed.count { it.kind == kind },
          "seed $seed misses copies of the required ${kind.label}"
        )
      }
    }
  }

  /** Against the rolls rather than `count * chance`, so roll noise cannot hide a placement loss or fake one. */
  @Test
  fun `most rolled copies find a place, and none appears without a roll`() {
    for (kind in PoiKind.entries) {
      val placed = copiesPlaced(kind)
      val rolled = copiesRolled(kind)

      assertTrue(
        placed >= rolled * MIN_SHARE_OF_ROLLED,
        "${kind.label} placed $placed of $rolled rolled copies. A shortfall this large is a filter most worlds " +
          "cannot satisfy. Measured: ${census()}"
      )
      assertTrue(placed <= rolled, "${kind.label} placed $placed copies but rolled only $rolled. ${census()}")
    }
  }

  // --- They are distinct things, in the right place -------------------------------------------------

  @Test
  fun `copies of an entry carry distinct roll indices below its count`() {
    for ((seed, placed) in worlds) {
      for ((kind, group) in placed.groupBy { it.kind }) {
        val copies = group.map { it.copy }

        assertEquals(copies.distinct(), copies, "seed $seed holds two ${kind.label} with one copy index")
        assertTrue(copies.all { it in 0 until kind.count }, "seed $seed ${kind.label} copies $copies")
      }
    }
  }

  @Test
  fun `landmarks keep their spacing`() {
    val params = WorldParams.DEFAULT.poi

    for ((seed, placed) in worlds) {
      for ((i, a) in placed.withIndex()) {
        for (b in placed.drop(i + 1)) {
          val distance = a.distanceTo(b)
          val sameKind = if (a.kind == b.kind) a.kind.minSpacing else 0.0
          val needed = max(sameKind, params.landmarkClearance)

          assertTrue(
            distance >= needed,
            "seed $seed: ${a.kind.label} #${a.copy} and ${b.kind.label} #${b.copy} are ${distance.roundToInt()} m " +
              "apart, closer than $needed"
          )
        }
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

  // --- The same world twice is the same world -------------------------------------------------------

  @Test
  fun `placement is a function of the seed`() {
    val once = placedIn(REPEAT_SEED)
    val twice = placedIn(REPEAT_SEED)

    assertEquals(
      once.map { "${it.kind.name}#${it.copy}@${it.x},${it.y}" },
      twice.map { "${it.kind.name}#${it.copy}@${it.x},${it.y}" },
      "two builds of seed $REPEAT_SEED disagree about where its landmarks are"
    )
  }

  // --- A required landmark never goes missing quietly -----------------------------------------------

  @Test
  fun `a required landmark with nowhere to stand fails the world`() {
    // Every position is closer than this to some settlement, so nothing can stand anywhere.
    val nowhere = WorldParams.DEFAULT.copy(poi = PoiParams(settlementClearance = 1e9))

    val error = assertFailsWith<PoiPlacementException> {
      StandardWorld.build(
        StandardWorld.demoConfig(FIRST_SEED).copy(widthCells = 64, heightCells = 64),
        params = nowhere
      )
    }

    assertTrue(PoiKind.BROKEN_OBELISK.name in error.message.orEmpty(), error.message)
  }

  private companion object {

    /** Enough that a 0.25-chance entry is all but certain to appear: `0.75^24` is under one in a thousand. */
    const val SEEDS = 24

    const val FIRST_SEED = 1L

    /** A 192 km world: big enough for the latitude-bound biomes three of the entries need. */
    const val CELLS = 192

    /** Outside the sweep's range, so the determinism check is a fresh build. */
    const val REPEAT_SEED = 4_242L

    /** Share of rolled copies that must stand. Below it a filter fails most worlds, not one without the biome. */
    const val MIN_SHARE_OF_ROLLED = 0.45

    class Placed(val kind: PoiKind, val copy: Int, val x: Double, val y: Double, val biome: Biome) {

      fun distanceTo(other: Placed): Double {
        return sqrt((x - other.x) * (x - other.x) + (y - other.y) * (y - other.y))
      }
    }

    /**
     * Reduced as it is built, so two dozen worlds of rasters are never resident at once. `by lazy` because an
     * eager initialiser would deadlock the sweep's threads on this class's static initialiser.
     */
    val worlds: List<Pair<Long, List<Placed>>> by lazy {
      val seeds = (0 until SEEDS).map { FIRST_SEED + it }
      seeds.zip(TestWorlds.sweep(seeds.map(::configOf)) { read(it) })
    }

    fun configOf(seed: Long) = StandardWorld.demoConfig(seed).copy(widthCells = CELLS, heightCells = CELLS)

    /** A fresh build, never from the cache: the determinism test builds the same seed twice. */
    fun placedIn(seed: Long): List<Placed> {
      return read(StandardWorld.build(configOf(seed)))
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
            copy = marker.attribute(PoiChannels.COPY).toInt(),
            x = marker.position.x,
            y = marker.position.y,
            // Nearest, never interpolated: a biome ordinal has no midpoint. The same cell the stage read.
            biome = Biome.entries[biome[(marker.position.x / metres).toInt(), (marker.position.y / metres).toInt()]]
          )
        }
        .sortedWith(compareBy({ it.kind.ordinal }, { it.copy }))
    }

    fun copiesRolled(kind: PoiKind): Int {
      return worlds.sumOf { (seed, _) -> PoiStage.rolledCopies(seed, kind).size }
    }

    fun copiesPlaced(kind: PoiKind): Int {
      return worlds.sumOf { (_, placed) -> placed.count { it.kind == kind } }
    }

    /** The whole table, for a failure message. A count on its own never says which entry starved. */
    fun census(): String {
      return PoiKind.entries.joinToString(", ") {
        "${it.name}=${copiesPlaced(it)}/${copiesRolled(it)}"
      }
    }
  }
}
