package net.bestia.zone.ecs.spawn.ambient

import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.zone.world.WorldGenConfig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * That the ring around a town follows the town rather than a radius.
 *
 * This is the requirement that motivated measuring from buildings at all. A disc around a settlement centre
 * is easy and wrong in both directions at once: settlements differ by a factor of ten in size between a
 * hamlet and a city, and none of them is round - `TownStage` lays plots along whatever shape the ground
 * allows, so a river town is a ribbon and a coastal one is a crescent. One radius therefore either leaves
 * creatures standing in a city's streets or empties square kilometres of open country around a hamlet.
 *
 * The last test is the one that actually proves it, and it is the reason a manual look at the game cannot:
 * a circular fallback would pass every other assertion here perfectly.
 */
class TownClearanceTest {

  private val settings = WorldGenConfig()
  private val config = WorldConfig(
    seed = DEV_SEED,
    widthCells = settings.widthCells,
    heightCells = settings.heightCells,
    baseResolution = Resolution(settings.cellSizeMetres),
    seaLevel = settings.seaLevelMetres,
    chunkSize = settings.chunkSize,
    chunkHeight = settings.chunkHeight,
    voxelSize = settings.voxelSizeMetres,
    wrapX = settings.wrapX,
    wrapY = settings.wrapY
  )

  private val generated = StandardWorld.build(config)
  private val settlements = StandingSettlements.of(generated)
  private val sut = TownClearance(generated, settlements, CLEARANCE_TILES)

  /** The largest standing settlement, so there is a real street plan to follow rather than a few huts. */
  private val town = settlements.let {
    var best: StandingSettlements.Entry? = null
    for (candidate in candidates()) {
      if (best == null || candidate.footprintRadius > best.footprintRadius) best = candidate
    }
    requireNotNull(best) { "the dev world has no standing settlement to measure" }
  }

  @Test
  fun `the middle of a town is closed to creatures`() {
    assertTrue(
      sut.blocks(town.x.toLong(), town.y.toLong()),
      "the centre of a ${town.tier} is not blocked, so nothing would keep creatures out of its streets"
    )
  }

  @Test
  fun `open country well beyond a town is not closed`() {
    val far = (town.footprintRadius + CLEARANCE_TILES + 5_000).toLong()

    assertFalse(
      sut.blocks(town.x.toLong() + far, town.y.toLong()),
      "ground ${far} m from a ${town.tier} is blocked, so the ring is reaching far past the town"
    )
  }

  /**
   * The assertion the whole approach exists for: how far the ring reaches depends on which way you walk.
   *
   * Sampling one fixed distance all the way round proves nothing - pick a radius inside the built area and
   * every bearing is closed however the boundary is shaped. So this walks *outwards* along each bearing and
   * records where blocking stops, which is the ring's own radius in that direction. A boundary that follows
   * the buildings gives a spread of those radii; a fallback to `footprintRadius` gives the same number 72
   * times and fails here while passing every other assertion in this file.
   */
  @Test
  fun `how far the ring reaches depends on the direction`() {
    val limit = town.footprintRadius + CLEARANCE_TILES + 1_000
    val edges = ArrayList<Double>(BEARINGS)

    for (step in 0 until BEARINGS) {
      val bearing = 2.0 * Math.PI * step / BEARINGS
      var edge = 0.0
      var radius = 0.0
      while (radius <= limit) {
        val x = (town.x + cos(bearing) * radius).toLong()
        val y = (town.y + sin(bearing) * radius).toLong()
        if (sut.blocks(x, y)) edge = radius
        radius += STEP_METRES
      }
      edges.add(edge)
    }

    val nearest = edges.min()
    val furthest = edges.max()
    val spread = furthest - nearest

    println(
      "${town.tier} footprint ${town.footprintRadius.toInt()} m: the ring ends between ${nearest.toInt()} " +
          "and ${furthest.toInt()} m from the centre depending on the bearing (spread ${spread.toInt()} m)"
    )

    assertTrue(furthest > 0.0, "no bearing is blocked at all, so the ring is not reaching the town")
    assertTrue(
      spread > TownClearance.MASK_CELL_METRES,
      "the ring ends the same distance out on every one of $BEARINGS bearings (spread ${spread.toInt()} m), " +
          "so it is a circle rather than the town's own outline - which is what a radius fallback looks like"
    )
  }

  private fun candidates(): List<StandingSettlements.Entry> {
    // `coveringWithin` with a reach past any footprint returns whatever stands near a probe; sweeping a
    // coarse grid is enough to find the world's larger towns without exposing the index's internals.
    val found = LinkedHashSet<StandingSettlements.Entry>()
    val extent = settings.widthCells * settings.cellSizeMetres
    var y = 0.0
    while (y < extent) {
      var x = 0.0
      while (x < extent) {
        found.addAll(settlements.coveringWithin(x, y, 0.0))
        x += PROBE_METRES
      }
      y += PROBE_METRES
    }
    return found.toList()
  }

  private companion object {
    /** `application.yml`'s pinned seed, so this measures the world the dev server actually runs. */
    const val DEV_SEED = 11_753_242L

    /** `application.yml`'s own `ambient-spawn.town-clearance-tiles`. */
    const val CLEARANCE_TILES = 100L

    const val BEARINGS = 72

    /** Fine enough to resolve a boundary quantised to `TownClearance.MASK_CELL_METRES`. */
    const val STEP_METRES = 16.0

    /** Coarse enough to sweep a 128 km world quickly, fine enough to land inside a village's footprint. */
    const val PROBE_METRES = 250.0
  }
}
