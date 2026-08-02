package net.bestia.worldgen.history

import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.resource.DepositChannels
import net.bestia.worldgen.karst.CaveChannels
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.max
import kotlin.math.min

/**
 * One place a special site could be built, found by reading the terrain.
 *
 * [quality] ranks candidates against each other and means something different per kind - ore richness for a
 * mine, how enclosed a saddle is for a fort. [detail] carries the one integer a kind needs to name what it is
 * built on, which today is only the mine's [net.bestia.worldgen.resource.ResourceType] ordinal.
 */
class SiteCandidate(
  val position: Vec2d,
  val quality: Double,
  val detail: Int = -1,
  /** Metres above sea level for a candidate that is not on the ground, or NaN. Only caves use it. */
  val elevation: Double = Double.NaN
)

/**
 * Where each kind of built site *could* go. Terrain, not history.
 *
 * **The split is the point.** Deciding where a fort can stand needs the elevation grid, the ore deposits and
 * the distance-to-ocean field; deciding whether a civilisation ever builds one needs a thousand years of war
 * and technology. Those are different jobs with different inputs, so the terrain half is done here - in the
 * stage, which has a [GenContext] - and handed to [HistorySim] as plain data. The simulation stays a pure
 * function over facts, which is what lets it be tested without a world.
 *
 * It is also the same division the architecture document already makes about settlements: **history does not
 * place settlements**, they are already where the land is good and history dates them. A built site is placed
 * by the ground and founded by the simulation, which is that rule one level down.
 */
class SpecialSiteCandidates(
  val mines: List<SiteCandidate>,
  val monasteries: List<SiteCandidate>,
  val forts: List<SiteCandidate>,
  val lighthouses: List<SiteCandidate>,

  /**
   * The back of every cave system: somewhere to hide something and not be followed.
   *
   * Not a place anybody *builds*, unlike the four above, which is why it is worth saying what it is doing in
   * this class. The division this class exists for still holds - the terrain decides where a cave's deep end
   * is, and a thousand years of war decides whether anybody ever had reason to run to one.
   */
  val caves: List<SiteCandidate> = emptyList()
) {

  companion object {

    val EMPTY = SpecialSiteCandidates(emptyList(), emptyList(), emptyList(), emptyList())

    /**
     * Reads the world for every kind of candidate.
     *
     * Every scan is capped and every result is mutually separated, because these lists are consumed by a
     * simulation that founds at most one site per civilisation per pass - so a thousand candidates would cost
     * a thousand times as much to build and be indistinguishable in the output from twenty.
     */
    fun read(
      ctx: GenContext,
      region: CellRegion,
      facts: List<SiteFacts>,
      params: HistoryParams
    ): SpecialSiteCandidates {
      val elevation = ctx.layers.float(LayerId.ELEVATION)
      val habitability = ctx.layers.float(LayerId.HABITABILITY)
      val distanceToOcean = ctx.layers.float(LayerId.DISTANCE_TO_OCEAN)
      val waterLevel = ctx.layers.float(LayerId.WATER_LEVEL)
      val seaLevel = ctx.config.seaLevel

      return SpecialSiteCandidates(
        mines = mines(ctx, region, facts, params, elevation, seaLevel),
        monasteries = monasteries(region, facts, params, elevation, habitability, waterLevel, seaLevel),
        forts = forts(region, facts, params, elevation, waterLevel, seaLevel),
        lighthouses = lighthouses(region, facts, params, elevation, distanceToOcean, seaLevel),
        caves = caves(ctx, region)
      )
    }

    /**
     * Every ore deposit rich enough, shallow enough and close enough to somewhere that could work it.
     *
     * The deposits are read off the `ORE_DEPOSIT` markers rather than off [LayerId.RESOURCE_VALUE], which is
     * the whole reason the vector tier carries them - the same argument `HistoryStage.volcanismField` makes
     * about faults. The smoothed field says "there is something worth having near here" and cannot say *what*,
     * and a mine that cannot name what it extracts is a hole in the ground.
     *
     * A mine sits **at** its deposit, so the join is positional and an invariant can check it by looking for a
     * deposit under the mine. That is cheaper and harder to get wrong than storing a feature id, which would
     * lose its low bits to a station channel's `Double` anyway.
     */
    private fun mines(
      ctx: GenContext,
      region: CellRegion,
      facts: List<SiteFacts>,
      params: HistoryParams,
      elevation: FloatLayer,
      seaLevel: Double
    ): List<SiteCandidate> {
      if (facts.isEmpty()) return emptyList()

      val out = ArrayList<SiteCandidate>()

      for (feature in ctx.features.query(region.toWorld())) {
        if (feature.kind != FeatureKind.ORE_DEPOSIT) continue
        val marker = feature as? PointMarker ?: continue

        val richness = runCatching { marker.attribute(DepositChannels.RICHNESS) }.getOrNull() ?: continue
        val depth = runCatching { marker.attribute(DepositChannels.DEPTH) }.getOrNull() ?: continue
        val type = runCatching { marker.attribute(DepositChannels.TYPE) }.getOrNull()?.toInt() ?: continue

        if (richness < params.mineRichness) continue
        if (depth > params.mineDepth) continue

        // Never underwater. An offshore orebody is a real thing and a medieval civilisation cannot work it.
        if (!isDryGround(elevation, marker.position, seaLevel, params.siteFreeboard)) continue

        // Somebody has to be near enough to have found it and to carry the ore away.
        val nearest = facts.minOf { it.position.distanceTo(marker.position) }
        if (nearest > params.mineRange) continue

        out.add(SiteCandidate(marker.position, richness, type))
      }

      return out.sortedByDescending { it.quality }.let { separate(it, params.siteSeparation, params.maxCandidates) }
    }

    /**
     * The deepest point of each cave system, as a place a hoard could be left.
     *
     * **The furthest station from the way in**, which is the whole rule and is worth stating as a rule: a hoard
     * is hidden by somebody who does not want it found, so it goes at the back. It also puts the treasure a
     * real walk from the entrance rather than in the first chamber, which is the difference between a cave
     * being a place and a cave being a container.
     *
     * One candidate per system, keyed by the system index the passages carry, so two hoards cannot end up in
     * the same cave by two different routes.
     */
    private fun caves(ctx: GenContext, region: CellRegion): List<SiteCandidate> {
      val features = ctx.features.query(region.toWorld())

      val mouths = HashMap<Int, MutableList<Vec2d>>()
      for (feature in features) {
        if (feature.kind != FeatureKind.CAVE_ENTRANCE) continue
        val marker = feature as? PointMarker ?: continue
        val system = runCatching { marker.attribute(CaveChannels.SYSTEM) }.getOrNull()?.toInt() ?: continue
        mouths.getOrPut(system) { ArrayList() }.add(marker.position)
      }
      if (mouths.isEmpty()) return emptyList()

      val best = HashMap<Int, SiteCandidate>()
      for (feature in features) {
        if (feature.kind != FeatureKind.CAVE_PASSAGE) continue
        val passage = feature as? MarkerFeature ?: continue
        val stations = passage.stations ?: continue
        val system = runCatching { stations.valueAt(stations.channel(CaveChannels.SYSTEM), 0) }
          .getOrNull()?.toInt() ?: continue
        val ways = mouths[system] ?: continue
        val floorChannel = runCatching { stations.channel(CaveChannels.FLOOR) }.getOrNull() ?: continue

        for (i in 0 until passage.centerline.vertexCount) {
          val at = passage.centerline.points[i]
          val fromDaylight = ways.minOf { it.distanceTo(at) }
          val current = best[system]
          if (current != null && current.quality >= fromDaylight) continue

          best[system] = SiteCandidate(
            position = at,
            quality = fromDaylight,
            detail = system,
            // On the floor, not in it. The hoard is a thing standing in the passage.
            elevation = stations.valueAt(floorChannel, i)
          )
        }
      }

      return best.values.sortedByDescending { it.quality }
    }

    /**
     * Remote, poor, high ground: where a religious house goes.
     *
     * Both halves of "remote *and* defensible" come from inputs that already exist. Low [LayerId.HABITABILITY]
     * is the poor half - a monastery on the best farmland in the province is a manor, not a monastery - and
     * height above the sea is the defensible half. The clearance test is the idiom `roadsideInns` already uses
     * to keep an inn out of a town.
     */
    private fun monasteries(
      region: CellRegion,
      facts: List<SiteFacts>,
      params: HistoryParams,
      elevation: FloatLayer,
      habitability: FloatLayer,
      waterLevel: FloatLayer,
      seaLevel: Double
    ): List<SiteCandidate> = scan(region, params) { position ->
      val ground = elevation.sampleBilinear(position.x, position.y)
      if (!isDryGround(elevation, position, seaLevel, params.siteFreeboard)) return@scan null
      if (!isClearOfStandingWater(waterLevel, region, position)) return@scan null

      // Far from anywhere. This is the definition of the site, not a tie-break, so it is a hard filter.
      if (facts.any { it.position.distanceTo(position) < params.monasteryClearance }) return@scan null

      val poverty = 1.0 - habitability.sampleBilinear(position.x, position.y).coerceIn(0.0, 1.0)
      val height = ((ground - seaLevel) / MONASTERY_REFERENCE_HEIGHT).coerceIn(0.0, 1.0)

      // A floor on each factor rather than a bare product, for the reason ClosedBasins records: a product of
      // sub-unit preferences is small almost everywhere, and scoring every real site at 0.07 makes the ranking
      // meaningless and sends every world to its single best candidate.
      SiteCandidate(position, (0.3 + 0.7 * poverty) * (0.3 + 0.7 * height))
    }

    /**
     * Saddles: a low point on a ridge, which is where a road crosses and therefore where a fort pays for itself.
     *
     * Deliberately its **own** measure rather than a call into `SettlementStage.passQuality`, and the reason is
     * layering rather than laziness - sibling stage packages may not call into one another, and the two want
     * different things anyway. That one biases *placement* towards a pass so a city ends up commanding it; this
     * locates ground a fort can sit on. They are allowed to disagree, and neither is derived from the other.
     *
     * A saddle is high ground with a low neighbour on two opposite sides and high ground on the other two: the
     * shape of a gap. Sampled at a stride, because a pass is a kilometre-scale feature and testing every cell
     * would find the same gap nine times.
     */
    private fun forts(
      region: CellRegion,
      facts: List<SiteFacts>,
      params: HistoryParams,
      elevation: FloatLayer,
      waterLevel: FloatLayer,
      seaLevel: Double
    ): List<SiteCandidate> = scan(region, params) { position ->
      val ground = elevation.sampleBilinear(position.x, position.y)
      if (!isDryGround(elevation, position, seaLevel, params.siteFreeboard)) return@scan null
      if (!isClearOfStandingWater(waterLevel, region, position)) return@scan null

      // Out of town, but not out in the wilderness - a frontier post guards a road somebody uses.
      val nearest = facts.minOf { it.position.distanceTo(position) }
      if (nearest < params.fortClearance || nearest > params.fortRange) return@scan null

      val step = params.saddleSpan
      val west = elevation.sampleBilinear(position.x - step, position.y)
      val east = elevation.sampleBilinear(position.x + step, position.y)
      val south = elevation.sampleBilinear(position.x, position.y - step)
      val north = elevation.sampleBilinear(position.x, position.y + step)

      // Two opposite shoulders above and two opposite approaches below, whichever axis they fall on.
      val eastWestGap = min(west, east) - ground
      val northSouthGap = min(north, south) - ground
      val shoulder = max(eastWestGap, northSouthGap)
      val through = -min(eastWestGap, northSouthGap)

      if (shoulder < params.saddleRelief || through < params.saddleRelief) return@scan null

      SiteCandidate(position, min(shoulder, through))
    }

    /**
     * Coastal high ground clear of any settlement: a headland with a light on it.
     *
     * Uses [SiteFacts.coastal]'s field - [LayerId.DISTANCE_TO_OCEAN] - which had no reader at all before this,
     * so adding one changes nothing that already worked. The candidate must be *on land and near the sea*, which
     * is a band rather than a threshold: a cell at zero distance to ocean is the sea.
     */
    private fun lighthouses(
      region: CellRegion,
      facts: List<SiteFacts>,
      params: HistoryParams,
      elevation: FloatLayer,
      distanceToOcean: FloatLayer,
      seaLevel: Double
    ): List<SiteCandidate> = scan(region, params) { position ->
      val ground = elevation.sampleBilinear(position.x, position.y)
      // A lower bar than the others on purpose: a light belongs on the rocks, and demanding the same freeboard
      // as a fort would move every one of them inland off the headland it exists to stand on.
      if (!isDryGround(elevation, position, seaLevel, params.lighthouseFreeboard)) return@scan null

      val toSea = distanceToOcean.sampleBilinear(position.x, position.y)
      if (toSea > params.lighthouseRange) return@scan null

      // A light inside a town is a lamp. The clearance is what makes it a landmark on the approach.
      if (facts.any { it.position.distanceTo(position) < params.lighthouseClearance }) return@scan null

      // Prefer a headland: high for how close to the water it is, so the light carries.
      SiteCandidate(position, (ground - seaLevel) / max(1.0, toSea))
    }

    /**
     * Samples the world on a stride and keeps the best mutually separated candidates.
     *
     * A stride rather than every cell because all three scans look for kilometre-scale ground, and because this
     * runs once per world and the world can be sixteen million cells.
     */
    private inline fun scan(
      region: CellRegion,
      params: HistoryParams,
      score: (Vec2d) -> SiteCandidate?
    ): List<SiteCandidate> {
      val metres = region.resolution.metresPerCell
      val stride = max(1, (params.candidateStride / metres).toInt())
      val found = ArrayList<SiteCandidate>()

      var y = 0
      while (y < region.height) {
        var x = 0
        while (x < region.width) {
          val position = Vec2d((region.minX + x + 0.5) * metres, (region.minY + y + 0.5) * metres)
          score(position)?.let { found.add(it) }
          x += stride
        }
        y += stride
      }

      found.sortByDescending { it.quality }
      return separate(found, params.siteSeparation, params.maxCandidates)
    }

    /**
     * Greedy separation over a list already sorted best first.
     *
     * Without it a scan returns the same hilltop nine times over and the simulation founds nine monasteries in
     * one valley - the shape of bug that made a settlement's own occupancy push its civ under its expansion
     * threshold.
     */
    private fun separate(
      candidates: List<SiteCandidate>,
      separation: Double,
      limit: Int
    ): List<SiteCandidate> {
      val kept = ArrayList<SiteCandidate>()

      for (candidate in candidates) {
        if (kept.size >= limit) break
        if (kept.any { it.position.distanceTo(candidate.position) < separation }) continue
        kept.add(candidate)
      }

      return kept
    }

    /**
     * Whether a site's whole footprint is out of the water, not merely its centre.
     *
     * **Freeboard rather than a footprint scan, and the difference is the resolution.** `probe -Pon=monastery`
     * showed a cloister with a fifth of its garth under water: the centre passed `elevation > seaLevel`, and the
     * eighty-metre square around it did not. Sampling the corners would not have caught it either - the
     * elevation raster is a kilometre grid and a forty-metre structure sits well inside one cell, so every
     * sample within the footprint returns nearly the same interpolated value. The water comes from the *chunk
     * tier's* detail, which the world tier cannot see.
     *
     * So the guard is a height margin instead: require enough elevation above sea level that the fine-scale
     * detail cannot dip below it. That is the same shape of answer as `WorldHeightField`'s relief being bounded,
     * and it is a claim about the detail noise's amplitude rather than about any particular cell.
     */
    private fun isDryGround(
      elevation: FloatLayer,
      at: Vec2d,
      seaLevel: Double,
      freeboard: Double
    ): Boolean = elevation.sampleBilinear(at.x, at.y) - seaLevel >= freeboard

    /**
     * Whether a cell and all eight of its neighbours are free of standing water.
     *
     * **Freeboard against sea level is not enough, because a lake is water too.** `probe -Pon=monastery` showed
     * a cloister with a fifth of its garth under water even after the freeboard went in, and the reason is that
     * [isDryGround] compares elevation to *sea* level: a lake sitting at five hundred metres is five hundred
     * metres of freeboard and still wet. `WATER_LEVEL` is the layer that knows about both, being NaN exactly
     * where there is no standing water.
     *
     * A full cell ring rather than the cell itself, because the shoreline crosses a cell: the site's own cell
     * can be dry while the water begins forty metres away, which is inside a monastery's precinct. One kilometre
     * of clearance is generous for a structure tens of metres across, and there are dozens of candidates, so
     * being strict here costs nothing.
     *
     * Applied to monasteries and forts and **not** to mines or lighthouses, deliberately. A mine goes where the
     * ore is and a lakeside adit is a real thing; a lighthouse belongs on the rocks, and this test would move
     * every one of them inland off the headland it exists to stand on.
     */
    private fun isClearOfStandingWater(waterLevel: FloatLayer, region: CellRegion, at: Vec2d): Boolean {
      val metres = region.resolution.metresPerCell
      val cellX = (at.x / metres).toInt()
      val cellY = (at.y / metres).toInt()

      for (dy in -1..1) {
        for (dx in -1..1) {
          if (!waterLevel[cellX + dx, cellY + dy].isNaN()) return false
        }
      }

      return true
    }

    /** Metres of relief above sea level at which the "high ground" term for a monastery saturates. */
    private const val MONASTERY_REFERENCE_HEIGHT = 1_200.0
  }
}
