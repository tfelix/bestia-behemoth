package net.bestia.worldgen.voxel

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Resolution
import java.util.Locale
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What [SurfaceSampler] draws where one surface meets another: a biome edge, the snow line, the blight front.
 *
 * Every one of them used to be a ruler line at the scale a player sees, and `probe -Pedges` counted it: half of all
 * biome edges within 2 m of a straight line over 150 m, the temperature thresholds nine times in ten. Each test
 * here measures the same layout with the fix and without it, so none can pass on a sampler that does nothing - the
 * control is required to come out straight, or unmixed, first.
 *
 * Synthetic layers, as in [BiomeDitherTest]: a single straight edge on demand is exactly what a real world does not
 * offer, and it is the case the fix has to break.
 */
class BiomeEdgeTest {

  private val region = CellRegion.world(16, 16, Resolution.KILOMETRE)
  private val cells = region.cellCount.toInt()

  /** GRASSLAND west of [EDGE_X], DRYLAND east of it, both certain, so only the edge itself can mix them. */
  private fun twoBiomes(edgeFray: Double) = SurfaceSampler(
    biome = IntLayer(LayerId.BIOME, region, IntArray(cells) { i ->
      if (i % region.width < EDGE_X.toInt() / 1000) Biome.GRASSLAND.ordinal else Biome.DRYLAND.ordinal
    }),
    soilDepth = FloatLayer(LayerId.SOIL_DEPTH, region, FloatArray(cells) { 1f }),
    waterLevel = FloatLayer(LayerId.WATER_LEVEL, region, FloatArray(cells) { Float.NaN }),
    lakeId = IntLayer(LayerId.LAKE_ID, region, IntArray(cells)),
    temperature = FloatLayer(LayerId.TEMPERATURE, region, FloatArray(cells) { 10f }),
    seed = 0x5EEDL,
    // No kilometre warp, so the control is a ruler at EDGE_X and the measurement is of the finer scales alone.
    boundaryJitter = 0.0,
    edgeFray = edgeFray,
    secondaryBiome = IntLayer(LayerId.BIOME_SECONDARY, region, IntArray(cells) { LayerId.NO_SECONDARY }),
    biomeConfidence = FloatLayer(LayerId.BIOME_CONFIDENCE, region, FloatArray(cells) { 1f })
  )

  /** A sampler over ramps: temperature rising and corruption rising eastwards, both linear in the cell index. */
  private fun ramps() = SurfaceSampler(
    biome = IntLayer(LayerId.BIOME, region, IntArray(cells) { Biome.GRASSLAND.ordinal }),
    soilDepth = FloatLayer(LayerId.SOIL_DEPTH, region, FloatArray(cells) { 1f }),
    waterLevel = FloatLayer(LayerId.WATER_LEVEL, region, FloatArray(cells) { Float.NaN }),
    lakeId = IntLayer(LayerId.LAKE_ID, region, IntArray(cells)),
    temperature = FloatLayer(LayerId.TEMPERATURE, region, FloatArray(cells) { i ->
      (COLDEST + DEGREES_PER_CELL * (i % region.width)).toFloat()
    }),
    seed = 0x5EEDL,
    corruption = FloatLayer(LayerId.CORRUPTION, region, FloatArray(cells) { i -> (i % region.width) / 15f })
  )

  /** Where bilinear temperature on [ramps] reaches [celsius]: cell centres sit at half a kilometre. */
  private fun xAt(celsius: Double) = ((celsius - COLDEST) / DEGREES_PER_CELL + 0.5) * 1000.0

  /** Where bilinear corruption on [ramps] reaches [severity]. */
  private fun xAtSeverity(severity: Double) = (severity * 15.0 + 0.5) * 1000.0

  /**
   * RMS distance of an edge from its best straight line, over a 150 m stretch at a metre a row.
   *
   * The edge in each row is read as the *area* west of it - the number of grass columns in a 300 m span - rather
   * than as the first change, so an island does not count as the edge moving and the number measures the edge's
   * shape alone. The fit is a line in y, so a gently slanted edge is not mistaken for a ragged one.
   */
  private fun residual(fromY: Double, isWest: (Double, Double) -> Boolean): Double {
    val rows = 150
    val positions = DoubleArray(rows) { r ->
      var west = 0
      for (c in 0 until 300) if (isWest(EDGE_X - 150.0 + c, fromY + r)) west++
      west.toDouble()
    }
    val meanY = (rows - 1) / 2.0
    val meanX = positions.average()
    var covariance = 0.0
    var variance = 0.0
    for (r in 0 until rows) {
      covariance += (r - meanY) * (positions[r] - meanX)
      variance += (r - meanY) * (r - meanY)
    }
    val slope = covariance / variance
    var squares = 0.0
    for (r in 0 until rows) {
      val off = positions[r] - (meanX + slope * (r - meanY))
      squares += off * off
    }
    return sqrt(squares / rows)
  }

  private fun medianResidual(isWest: (Double, Double) -> Boolean): Double =
    (0 until WINDOWS).map { residual(1_000.0 + it * 650.0, isWest) }.sorted()[WINDOWS / 2]

  /** Share of positions in a strip of columns, over most of the map's height, for which [test] holds. */
  private fun share(fromX: Double, untilX: Double, test: (Double, Double) -> Boolean): Double {
    var hits = 0
    var total = 0
    var y = 500.0
    while (y < 15_500.0) {
      var x = fromX
      while (x < untilX) {
        if (test(x, y)) hits++
        total++
        x += 1.0
      }
      y += 3.7
    }
    return hits.toDouble() / total
  }

  /** Share of neighbouring columns that differ, over a band of columns; a per-column coin flip gives about half. */
  private fun speckle(fromX: Double, untilX: Double, test: (Double, Double) -> Boolean): Double {
    var differing = 0
    var compared = 0
    var y = 500.0
    while (y < 6_500.0) {
      var x = fromX
      while (x < untilX) {
        val here = test(x, y)
        if (test(x + 1.0, y) != here) differing++
        if (test(x, y + 1.0) != here) differing++
        compared += 2
        x += 1.0
      }
      y += 1.0
    }
    return differing.toDouble() / compared
  }

  private fun f(v: Double) = "%.3f".format(Locale.ROOT, v)

  @Test
  fun `a biome edge is not a ruler line at the scale a player sees`() {
    val ruler = medianResidual { x, y -> twoBiomes(0.0).biomeAt(x, y) == Biome.GRASSLAND }
    assertTrue(ruler < 0.5, "the control should be a ruler at x = $EDGE_X, but strays ${f(ruler)} m")

    val frayed = twoBiomes(1.0)
    val ragged = medianResidual { x, y -> frayed.biomeAt(x, y) == Biome.GRASSLAND }
    assertTrue(
      ragged > MIN_RAGGED_METRES,
      "the frayed edge strays only ${f(ragged)} m from a straight line over 150 m; " +
          "it should bend by more than $MIN_RAGGED_METRES"
    )
  }

  @Test
  fun `both biomes take ground across the edge`() {
    // Both directions, and that is the point: the confidence dither mixes only the cell whose runner-up happens to
    // be the neighbour, so across most edges one side mixes and the other never does.
    val ruler = twoBiomes(0.0)
    assertEquals(0.0, share(EDGE_X - 10, EDGE_X) { x, y -> ruler.biomeAt(x, y) == Biome.DRYLAND }, 0.0)

    val frayed = twoBiomes(1.0)
    val dryInGrass = share(EDGE_X - 10, EDGE_X) { x, y -> frayed.biomeAt(x, y) == Biome.DRYLAND }
    val grassInDry = share(EDGE_X, EDGE_X + 10) { x, y -> frayed.biomeAt(x, y) == Biome.GRASSLAND }
    assertTrue(dryInGrass > MIN_CROSSING, "the grass side holds only ${f(dryInGrass)} dryland within 10 m")
    assertTrue(grassInDry > MIN_CROSSING, "the dry side holds only ${f(grassInDry)} grassland within 10 m")
  }

  @Test
  fun `a cell interior keeps its biome`() {
    // Exact rather than measured: fbm is bounded to [-1, 1], so no lookup moves further than the edge warp's
    // amplitude plus the fray's reach on either axis - 240 m. Beyond that a column can only read its own cell.
    val frayed = twoBiomes(1.0)
    assertEquals(0.0, share(EDGE_X - 600, EDGE_X - 250) { x, y -> frayed.biomeAt(x, y) != Biome.GRASSLAND }, 0.0)
    assertEquals(0.0, share(EDGE_X + 250, EDGE_X + 600) { x, y -> frayed.biomeAt(x, y) != Biome.DRYLAND }, 0.0)
  }

  @Test
  fun `the fray arrives in patches rather than as speckle`() {
    val frayed = twoBiomes(1.0)
    val rate = speckle(EDGE_X - 30, EDGE_X + 30) { x, y -> frayed.biomeAt(x, y) == Biome.GRASSLAND }
    assertTrue(rate < 0.10, "${f(100 * rate)}% of neighbouring columns differ across the edge - that is speckle")
  }

  @Test
  fun `the snow line is dithered across its band and certain outside it`() {
    val sampler = ramps()
    val snow = { x: Double, y: Double -> sampler.coverTemperatureAt(x, y) < SurfaceCover.SNOW_TEMPERATURE }
    val raw = { x: Double, y: Double -> sampler.temperatureAt(x, y) < SurfaceCover.SNOW_TEMPERATURE }

    // The control: the raster alone draws one straight line.
    val line = xAt(SurfaceCover.SNOW_TEMPERATURE)
    assertEquals(1.0, share(line - 20, line - 10, raw), 0.0)
    assertEquals(0.0, share(line + 10, line + 20, raw), 0.0)

    // All or nothing beyond the band - the offset is bounded - and a mix inside it, about even at the threshold.
    val colder = xAt(SurfaceCover.SNOW_TEMPERATURE - 0.65)
    val warmer = xAt(SurfaceCover.SNOW_TEMPERATURE + 0.65)
    assertEquals(1.0, share(colder - 20, colder, snow), 0.0)
    assertEquals(0.0, share(warmer, warmer + 20, snow), 0.0)

    val atLine = share(line - 5, line + 5, snow)
    assertTrue(atLine in 0.35..0.65, "snow covers ${f(atLine)} of the ground at the threshold, expected about half")

    val rate = speckle(line - 100, line + 100, snow)
    assertTrue(rate < 0.10, "${f(100 * rate)}% of neighbouring columns differ at the snow line - that is speckle")
  }

  @Test
  fun `blight covers about its own severity of the ground`() {
    // The raw field crowds around a half, so before it was ranked a quarter's severity blighted almost nothing and
    // three quarters almost everything - the dither collapsed into a contour of the corruption raster. Measured
    // ranked: 0.286 and 0.768.
    val sampler = ramps()
    for (severity in listOf(0.25, 0.75)) {
      val x = xAtSeverity(severity)
      val blighted = share(x - 5, x + 5) { px, py -> sampler.isBlightedAt(px, py) }
      assertTrue(
        blighted in severity - 0.08..severity + 0.08,
        "severity $severity blighted ${f(blighted)} of the ground"
      )
    }
  }

  private companion object {
    const val EDGE_X = 8_000.0
    const val WINDOWS = 15

    const val COLDEST = -6.0
    const val DEGREES_PER_CELL = 0.8

    /** Measured 13.2 m on this layout, against exactly 0 for the control; a third of that still fails a ruler. */
    const val MIN_RAGGED_METRES = 4.0

    /** Measured 0.42 and 0.43 for the two sides, against exactly 0 for the control. */
    const val MIN_CROSSING = 0.15
  }
}
