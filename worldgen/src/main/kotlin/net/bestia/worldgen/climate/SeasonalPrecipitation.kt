package net.bestia.worldgen.climate

import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.LayerStore
import net.bestia.worldgen.vector.Polyline
import kotlin.math.floor

/**
 * The annual rainfall curve, read from the four seasonal layers [ClimateStage] emits.
 *
 * The four layers are quarterly totals in millimetres and they sum to [LayerId.PRECIPITATION]. What a consumer
 * usually wants is neither of those things: a food model asks whether the rain arrives while the grain is in
 * the ground, which is a question about a *month*. This is the adapter between the two, and it is a pure
 * function over layers - no state, nothing cached, safe from any thread.
 *
 * **Monthly fields are derived rather than stored**, which is the whole reason there are four layers and not
 * twelve. Twelve rasters is twelve times the memory and twelve advection sweeps to hold a curve that four
 * control points and a periodic spline already describe to well inside the accuracy of the sweep that produced
 * them. The architecture document's rule is not to add a layer without a reader; the corollary is not to store
 * what a reader can interpolate.
 *
 * Sampling is by **world position in metres**, never by cell index. The climate grid is four times coarser than
 * the heightfield, so indexing it with a kilometre-grid coordinate does not fail, it *clamps* - which is how
 * the economy stage once read the polar temperature at the grid's corner for every catchment in the world.
 */
class SeasonalPrecipitation(private val seasons: List<FloatLayer>) {

  init {
    require(seasons.size == COUNT) { "Expected $COUNT seasonal layers, got ${seasons.size}" }
  }

  /**
   * Rainfall in millimetres over one whole season, at a world position.
   *
   * [season] is taken modulo [COUNT], so a caller stepping a counter past the end of the year wraps rather
   * than throwing - the year is a loop and an index into it should behave like one.
   */
  fun atSeason(season: Int, worldX: Double, worldY: Double): Double =
    seasons[Math.floorMod(season, COUNT)].sampleBilinear(worldX, worldY)

  /**
   * Rainfall in millimetres during [month], at a world position. Month 0 is the first month of the year and
   * the value is periodic, so 12 is 0 again and -1 is the month before it.
   *
   * A periodic Catmull-Rom through the four seasonal values, using the same spline
   * [net.bestia.worldgen.vector.StationTable] runs along a river rather than a second copy of the maths. The
   * one difference is the ends: a station table *clamps* them, because a river must not gain width past its
   * mouth if the spline overshoots, and here the ends wrap instead. That is the point - December's curve is
   * shaped by January, so an annual cycle interpolated with clamped ends has a corner at the new year.
   *
   * Each seasonal layer is treated as the value at its quarter's **centre** rather than its start, since a
   * quarterly total is a statement about the whole quarter. So the four samples sit at months 1.5, 4.5, 7.5
   * and 10.5, and asking for month 1.5 returns spring exactly.
   *
   * Clamped at zero. Catmull-Rom is an interpolating spline, not a monotone one, so a sharp monsoon - a wet
   * season against three dry ones - can undershoot slightly between the dry samples, and a negative rainfall
   * is not a thing a consumer should have to defend against.
   */
  fun atMonth(worldX: Double, worldY: Double, month: Double): Double {
    val perSeason = ClimateStage.MONTHS_PER_YEAR / COUNT

    // Shifted by half a season so the samples land on quarter centres, then measured in seasons.
    val t = (month - perSeason / 2.0) / perSeason
    val i = floor(t).toInt()
    val frac = t - i

    val p0 = atSeason(i - 1, worldX, worldY)
    val p1 = atSeason(i, worldX, worldY)
    val p2 = atSeason(i + 1, worldX, worldY)
    val p3 = atSeason(i + 2, worldX, worldY)

    // A quarterly total spread over the months in that quarter. The spline gives the quarter's rate at this
    // instant in the year; a month is a third of a quarter of it.
    val quarterly = Polyline.catmullRom(p0, p1, p2, p3, frac)
    return (quarterly / perSeason).coerceAtLeast(0.0)
  }

  /**
   * Which season is wettest at a world position.
   *
   * The hemisphere-aware answer to "when is the wet season here", and the reason a consumer should not pick a
   * layer by name: [LayerId.PRECIPITATION_SUMMER] is a northern label, so south of the equator it holds the
   * dry season. Ties go to the earlier season, which only matters on a cell with no seasonality at all.
   */
  fun wettestSeason(worldX: Double, worldY: Double): Int {
    var best = 0
    var bestValue = Double.NEGATIVE_INFINITY
    for (season in 0 until COUNT) {
      val value = atSeason(season, worldX, worldY)
      if (value > bestValue) {
        bestValue = value
        best = season
      }
    }
    return best
  }

  companion object {

    /**
     * The four layers, in the order the year runs through them.
     *
     * This list *is* the season index: season 0 is [LayerId.PRECIPITATION_SPRING]. `ClimateStage` writes them
     * in this order and this class reads them back in it, so the two cannot disagree about which quarter is
     * which without the list itself changing.
     */
    val LAYERS = listOf(
      LayerId.PRECIPITATION_SPRING,
      LayerId.PRECIPITATION_SUMMER,
      LayerId.PRECIPITATION_AUTUMN,
      LayerId.PRECIPITATION_WINTER
    )

    val COUNT = LAYERS.size

    /**
     * Reads the four layers out of a store, or null when this world has no climate stage in it.
     *
     * Nullable rather than throwing because the stage tests each run a handful of stages and the viewer opens
     * on partial pipelines; a caller inside the pipeline has declared its dependency and can assert.
     */
    fun from(layers: LayerStore): SeasonalPrecipitation? {
      val found = LAYERS.map { layers[it] as? FloatLayer ?: return null }
      return SeasonalPrecipitation(found)
    }
  }
}
