package net.bestia.worldgen.voxel

import net.bestia.worldgen.coast.CoastChannels
import net.bestia.worldgen.coast.ShoreKind
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.roundToInt

/**
 * The coastline segments crossing one chunk, and what they say about a column.
 *
 * Built per chunk from the features the spatial index returned, with the channel indices resolved once out of
 * the column loop - the shape [PondWaterSampler] and [RiverWaterSampler] already have, and for the same
 * reasons.
 *
 * What it answers is not "am I near a line" but "which segment is *responsible* for this column, and what does
 * it say". Segments deliberately overlap, so nearness alone would give two answers at every junction; the
 * half-open arc-length claim is what makes exactly one of them authoritative.
 */
class CoastShoreSampler(features: List<VectorFeature>) {

  /** What one coastline segment says about a column. */
  class Shore(
    val kind: ShoreKind,
    /** Metres from the column to the waterline. Always positive; landward and seaward are not distinguished. */
    val distance: Double,
    val beachWidth: Double,
    val bermHeight: Double
  )

  /** One segment with its channels resolved, so the column loop does no string lookups. */
  private class Segment(
    val feature: MarkerFeature,
    val kind: Int,
    val width: Int,
    val berm: Int,
    val claimStart: Double,
    val claimEnd: Double,
    val reach: Double
  )

  private val segments: List<Segment> = features
    .asSequence()
    .filter { it.kind == FeatureKind.COASTLINE }
    .filterIsInstance<MarkerFeature>()
    .mapNotNull { feature ->
      // A segment whose table is missing a channel is a producer bug, but it must not take chunk generation
      // down with it - skip it and let the invariant harness be the thing that complains. Same treatment, and
      // same reasoning, as a river channel missing its geometry.
      runCatching {
        val stations = feature.stations!!
        val width = stations.channel(CoastChannels.BEACH_WIDTH)

        var widest = 0.0
        for (station in 0 until stations.stationCount) {
          val value = stations.valueAt(width, station)
          if (value > widest) widest = value
        }

        Segment(
          feature = feature,
          kind = stations.channel(CoastChannels.SHORE_KIND),
          width = width,
          berm = stations.channel(CoastChannels.BERM_HEIGHT),
          claimStart = stations.valueAt(stations.channel(CoastChannels.CLAIM_START), 0),
          claimEnd = stations.valueAt(stations.channel(CoastChannels.CLAIM_END), 0),
          reach = widest
        )
      }.getOrNull()
    }
    .toList()

  val isEmpty get() = segments.isEmpty()

  /**
   * What the shore says about this column, or null where no segment claims it.
   *
   * A pure function of world position, which is the chunk-seam guarantee: the projection and the claim are both
   * decided from the column's own coordinates and the feature's own geometry, so two chunks either side of a
   * beach agree column for column without consulting each other.
   */
  fun shoreAt(worldX: Double, worldY: Double): Shore? {
    if (segments.isEmpty()) return null

    var best: Segment? = null
    var bestDistance = Double.MAX_VALUE
    var bestU = 0.0

    for (segment in segments) {
      if (!segment.feature.bbox.expanded(segment.reach).contains(worldX, worldY)) continue

      val projection = segment.feature.centerline.project(Vec2d(worldX, worldY))

      // The claim, and it is the whole reason overlapping segments give one answer. A column that projects
      // outside this segment's claim belongs to the neighbour that laps over it.
      if (projection.s < segment.claimStart || projection.s >= segment.claimEnd) continue
      if (projection.distance >= bestDistance) continue

      best = segment
      bestDistance = projection.distance
      bestU = projection.u
    }

    val segment = best ?: return null
    val stations = segment.feature.stations!!

    // `valueAt` on a rounded station and never `sample`, because this channel is a category: Catmull-Rom across
    // a cliff meeting a marsh would hand back the kind whose ordinal sits between them. See `CoastChannels`.
    // `u` is already station parameter space - vertex i sits at exactly i - so the nearest station is the
    // rounded value and not a rescaled one.
    val station = bestU.roundToInt().coerceIn(0, stations.stationCount - 1)
    val kind = ShoreKind.entries.getOrNull(stations.valueAt(segment.kind, station).roundToInt()) ?: return null

    return Shore(
      kind = kind,
      distance = bestDistance,
      beachWidth = stations.sample(segment.width, bestU),
      bermHeight = stations.sample(segment.berm, bestU)
    )
  }
}
