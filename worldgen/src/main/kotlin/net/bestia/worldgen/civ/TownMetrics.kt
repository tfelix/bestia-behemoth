package net.bestia.worldgen.civ

import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.FootprintFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Profiles
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.abs

/**
 * What a generated town measures, as opposed to whether it is correct.
 *
 * `Invariants` already answers "is this town wrong" - nothing built in water, every walled town has a gate.
 * None of its checks answer "does this town look like a town", and that is the question every layout change is
 * actually judged on. Answering it by looking at one PNG per change does not survive a sweep, and does not
 * survive a reviewer either.
 *
 * A pure function over a finished [GeneratedWorld], like [SettlementSpawnPoints] and for the same reason:
 * nothing in the pipeline consumes it, so making it a stage would buy a `paramsVersion` and a
 * `pipelineVersion` move - invalidating every stored world - in exchange for nothing.
 *
 * ### What is deliberately not measured
 *
 * **Junction density.** A junction is a node of the street *graph*, and the graph does not survive into the
 * features: chains are resampled to `streetSpacing` on the way out, which moves every interior vertex, so a
 * T-junction is no longer a shared coordinate and counting shared vertices would count almost none of them.
 * Measuring it honestly means keeping the graph, which is a change to the stage rather than to this file.
 *
 * **Block area.** Blocks are `TownPatches`, which are never emitted - a `DISTRICT` is made from a group of
 * them later. [Measured.builtShare] is the part of the same question that the features can actually answer.
 *
 * **Dead ends.** The same missing graph, plus a second confound: a street is emitted as one feature per dry
 * stretch, so one street crossing water reads as two streets and four ends. Counting ends that no other street
 * comes near was written, measured and dropped - it was also the only part of this file that cost more than
 * linear time, being every endpoint against every other street.
 */
object TownMetrics {

  /**
   * One town, measured. Distances in metres, areas in square metres, shares in `[0,1]`.
   *
   * Every field is a number a change can move in a direction somebody intended, which is the bar for being
   * here at all - a statistic nobody would act on is noise in a table that has to stay readable.
   */
  data class Measured(
    val settlement: Int,
    val tier: SettlementTier,
    val buildings: Int,

    /** Total length of every street in the town. The denominator for the two rates below. */
    val streetMetres: Double,

    /**
     * Share of street length at the widest carriageway the town has.
     *
     * The street hierarchy, as one number. A town where this is near 1 has no hierarchy - every street is a
     * high street - and one where it is near 0 has a high street nobody can reach.
     */
    val wideShare: Double,

    /**
     * How crosswise the suburbs run: length-weighted mean of `|sin|` of the angle between a street and the
     * direction out from the town centre, outside the core. One is all ring, zero is all spoke.
     *
     * **This is the wheel detector**, and it is a *relative* one - read it as the same world before and after a
     * change, not against a threshold. It responds sharply to the thing it measures: shrinking `arcSpan` to a
     * stub took a 128 km world's median from 0.84 to 0.60. But its level also depends on how much of a town is
     * core, which varies by tier, so there is no single number that separates a good town from a bad one and
     * [ISOTROPIC] is a landmark rather than a pass mark.
     *
     * [ISOTROPIC] is where a network with no preferred orientation sits: 0.64, because the mean of `|sin|`
     * over a uniform angle is `2/pi` rather than the half it looks like it should be. A side street running
     * between two arteries pulls above it; a spoke pulls below.
     *
     * Measured outside [CORE_SHARE] of the built radius, to keep the `TownPatches` edges out of it - their
     * orientation is whatever the Voronoi partition left. The cut is approximate, and is why a city, which has
     * the most core, reads lower than a village, which has none.
     *
     * Radial concentration - how much street length piles up at a few radii - was measured here first and
     * dropped: the core carries far more street per hectare than the suburbs, so the statistic reported that
     * the middle of a town is busy, which is true of every town and moved by 5% when the rings moved by half.
     *
     * ### It cannot see a settlement laid along one way
     *
     * A `RoadsideVillage`'s spine runs through its own centre, so every segment of it points away from that
     * centre and reads as a perfect spoke: every hamlet in the reference world measures exactly zero. That is
     * arithmetic rather than a fault - there is nothing crosswise in a street village - but it means the
     * world's median is set by the settlements this cannot see. Read it per settlement, or over the grown
     * layouts only.
     */
    val tangentialShare: Double,

    /** Median building footprint. */
    val medianFootprint: Double,

    /** The 90th footprint percentile over the 10th: how much a town's biggest plots outsize its smallest. */
    val footprintSpread: Double,

    /**
     * Building footprint over district area, counting only the buildings that stand in a district.
     *
     * The open-space question the features can answer. Near 1 is a town with no yards, gardens or squares;
     * near 0 is a district that was claimed and never built on.
     *
     * Only the buildings inside one, because a district does not have to cover its settlement - a patch
     * holding too few buildings gets none, and the inferred path clusters rather than partitions. Divided
     * against every building in the town it stopped being a share at all: one village reported 148%.
     *
     * Zero therefore carries two meanings and the reader has to tell them apart: a settlement whose districts
     * were never built on, and a settlement that has no district to measure against. A hamlet is usually the
     * second - sixteen houses strung along one way cluster into no group large enough to name.
     */
    val builtShare: Double,

    /** Median distance from a building to the nearest widest-class street. */
    val metresToWideStreet: Double
  )

  /** Every standing town in the world, measured, in settlement-index order. */
  fun of(generated: GeneratedWorld): List<Measured> {
    val features = generated.world.features.all()

    val sites = features.filterIsInstance<PointMarker>()
      .filter { it.kind == FeatureKind.SETTLEMENT }
      .associateBy { it.attribute(SettlementChannels.INDEX).toInt() }

    val buildings = features.filterIsInstance<FootprintFeature>()
      .filter { it.kind == FeatureKind.BUILDING }
      .groupBy { it.attribute(BuildingChannels.SETTLEMENT).toInt() }

    val districts = features.filterIsInstance<AreaFeature>()
      .filter { it.kind == FeatureKind.DISTRICT }
      .groupBy { it.attribute(DistrictChannels.SETTLEMENT).toInt() }

    // Streets carry no settlement channel, so they are attributed the way `TownMain` already attributes them:
    // by proximity. Safe rather than approximate, because the tightest tier separation (3.5 km between two
    // hamlets) is an order of magnitude beyond the widest footprint those two would claim (130 m each).
    val streets = features.filterIsInstance<PolylineFeature>().filter { it.kind == FeatureKind.STREET }

    return sites.keys.sorted().mapNotNull { index ->
      val site = sites.getValue(index)
      val here = buildings[index].orEmpty()
      if (here.isEmpty()) return@mapNotNull null

      val tier = SettlementTier.entries[site.attribute(SettlementChannels.TIER).toInt()]
      val reach = tier.footprintRadius
      // The bounding box's centre, not an endpoint: a street that starts just outside the footprint and runs
      // into the town is the town's street, and which of its two ends came first is an emission detail.
      val near = streets.filter {
        site.position.distanceTo(Vec2d(it.bbox.centerX, it.bbox.centerY)) <= reach
      }

      measure(index, tier, site.position, reach, here, districts[index].orEmpty(), near)
    }
  }

  private fun measure(
    index: Int,
    tier: SettlementTier,
    centre: Vec2d,
    reach: Double,
    buildings: List<FootprintFeature>,
    districts: List<AreaFeature>,
    streets: List<PolylineFeature>
  ): Measured {
    val footprints = buildings.map { it.halfLength * it.halfWidth * 4.0 }.sorted()
    val districtArea = districts.sumOf { it.ring.area }
    // Measured by containment for `Districts.ofPatches`' reason: which buildings ended up in a district is not
    // the same question as which plots were cut there.
    val housedFootprint = buildings
      .filter { building -> districts.any { it.ring.contains(building.center) } }
      .sumOf { it.halfLength * it.halfWidth * 4.0 }

    val widths = streets.associateWith { widthOf(it) }
    val widest = widths.values.maxOrNull() ?: 0.0
    val arteries = streets.filter { widths.getValue(it) >= widest - WIDTH_TOLERANCE }
    val streetMetres = streets.sumOf { it.centerline.length }

    return Measured(
      settlement = index,
      tier = tier,
      buildings = buildings.size,
      streetMetres = streetMetres,
      wideShare = share(arteries.sumOf { it.centerline.length }, streetMetres),
      tangentialShare = tangentialShare(streets, centre, reach),
      medianFootprint = percentile(footprints, 0.5),
      footprintSpread = share(percentile(footprints, 0.9), percentile(footprints, 0.1)),
      builtShare = share(housedFootprint, districtArea),
      metresToWideStreet = percentile(
        buildings.map { building -> arteries.minOfOrNull { distanceTo(it.centerline, building.center) } ?: reach }
          .sorted(),
        0.5
      )
    )
  }

  /**
   * Length-weighted mean of how crosswise the suburb streets run. See [Measured.tangentialShare].
   *
   * Segment by segment rather than street by street, because a single grown street bends: charging its whole
   * length to the orientation of its endpoints would call a street that curves round the town a spoke.
   */
  private fun tangentialShare(streets: List<PolylineFeature>, centre: Vec2d, reach: Double): Double {
    var weighted = 0.0
    var total = 0.0

    for (street in streets) {
      val points = street.centerline.points
      for (i in 0 until points.size - 1) {
        val midpoint = (points[i] + points[i + 1]) * 0.5
        val out = midpoint - centre
        val radius = out.length
        if (radius < reach * CORE_SHARE) continue

        val along = points[i + 1] - points[i]
        val length = along.length
        if (length <= 0.0 || radius <= 0.0) continue

        // |cross| over the two lengths is |sin| of the angle between them, with no trigonometry and no branch
        // for which way round the street was emitted.
        val sine = abs(out.x * along.y - out.y * along.x) / (radius * length)
        weighted += sine * length
        total += length
      }
    }

    return share(weighted, total)
  }

  /** The carriageway width a street was stamped with, or zero if it carries no width channel. */
  private fun widthOf(street: PolylineFeature): Double {
    val channel = runCatching { street.stations.channel(Profiles.CHANNEL_HALF_WIDTH) }.getOrNull() ?: return 0.0
    return street.stations.valueAt(channel, 0) * 2.0
  }

  private fun distanceTo(line: Polyline, at: Vec2d): Double {
    return line.project(at).distance
  }

  private fun percentile(sorted: List<Double>, at: Double): Double {
    if (sorted.isEmpty()) return 0.0
    return sorted[((sorted.size - 1) * at).toInt()]
  }

  /** Guards every ratio here, so a town with no streets or no districts reports zero rather than a NaN. */
  private fun share(of: Double, total: Double): Double {
    if (total <= 0.0) return 0.0
    return of / total
  }

  /**
   * What [Measured.tangentialShare] reads as "no preferred orientation": the mean of `|sin|` over a uniform
   * angle, which is `2/pi` and not the half it looks like it should be.
   */
  const val ISOTROPIC = 2.0 / Math.PI

  /**
   * Share of the built radius treated as the core, and skipped by [tangentialShare].
   *
   * `TownPatches.CORE_SHARE` is the same fraction of the town's *outline*; this is the radial version of it,
   * and it does not have to be exact - it only has to keep the patch edges out of a measurement about the
   * streets the town grew.
   */
  private const val CORE_SHARE = 0.55

  /** Metres of width two streets may differ by and still count as the same class. Ranks differ by 1.67 m or more. */
  private const val WIDTH_TOLERANCE = 0.2

}
