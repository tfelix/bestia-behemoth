package net.bestia.worldgen.voxel

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.civ.RoadClass
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.fields.Noise
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Profiles
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * What the roads between settlements are made of, column by column: the part of a road a player actually sees.
 *
 * ### Why this exists
 *
 * A road used to be a height profile and nothing else. `Profiles.road` levels a bed ten to sixteen metres wide
 * and the surface cap then comes from the biome, so the bed came out as a strip of perfectly flat grass - on the
 * map a dashed line, underfoot nothing at all. Only a town's streets were paved (`TownStructures.pavingAt`).
 *
 * ### The tread is not the bed
 *
 * The bed is the right of way the road flattens; the *tread* is the strip traffic wore into it, or that somebody
 * paved, and it is narrower. Drawing the whole bed as road would give a ten metre ribbon of earth with ruled
 * edges - a modern verge-to-verge carriageway - where a medieval way was a beaten strip that wandered about
 * inside its right of way, widened where carts had gone round a mire and pinched where the grass was winning.
 * So per column, along the road's own arc length:
 *
 * - **The tread wanders** off the centreline, over a few tens of metres, and is clamped to stay inside the bed.
 *   This is the fine-scale wobble a player sees, and it costs no vertices - the centreline's own meander
 *   (`SettlementStage.meandered`) is the long swing.
 * - **It breathes**: its width varies along the road, a lot for a trail and almost not at all for a cobbled
 *   road, which somebody built to a width.
 * - **Its edge frays**: a 2D noise in world coordinates moves the edge by a fraction of a metre, so it is ragged
 *   rather than a line.
 *
 * Everything is a pure function of the road and of world position, never of the chunk asking, so two chunks
 * agree about a column on their shared border by construction - the same guarantee every feature here keeps.
 *
 * ### What each class is made of
 *
 * - [RoadClass.TRAIL]: packed earth, narrow, and at its narrowest it all but disappears under the grass.
 * - [RoadClass.DIRT_ROAD]: packed earth with standing mud in its two wheel ruts here and there, and a grassy crown
 *   down the middle on quieter stretches.
 * - [RoadClass.COBBLED]: cobbles of a near constant width, with a frayed verge of beaten earth either side where
 *   traffic stepped off the stone.
 *
 * Built per chunk from the features that chunk's query returned, and clipped to the segments that can reach the
 * chunk's own [bounds]: `Polyline.project` scans every segment, and a road is tens of kilometres long.
 */
internal class RoadSurfaces(features: List<VectorFeature>, bounds: Aabb) {

  private val roads: List<Road> = features
    .asSequence()
    .filter { it.kind == FeatureKind.ROAD }
    .filterIsInstance<PolylineFeature>()
    .mapNotNull { Road.of(it, bounds) }
    .toList()

  val isEmpty get() = roads.isEmpty()

  /**
   * The road surface over one column, or null where the column keeps whatever the biome gave it.
   *
   * @param puddles whether the ground here holds standing water at all: false in an arid biome, where a dirt
   *   road is dust, and below freezing, where it is frozen ruts. See [puddlesIn].
   */
  fun surfaceAt(worldX: Double, worldY: Double, puddles: Boolean = true): BlockType? {
    for (road in roads) {
      return road.surfaceAt(worldX, worldY, puddles) ?: continue
    }
    return null
  }

  private class Road(
    private val feature: PolylineFeature,
    private val roadClass: RoadClass,
    private val halfWidth: Int,
    /** Indices of the segments that can reach the chunk. */
    private val segments: IntArray,
    /** Where any of them can reach: their bounds, widened by the widest bed among them. */
    private val reach: Aabb
  ) {

    private val tread = Tread.of(roadClass)
    private val points = feature.centerline.points
    private val seed = GenRng.mix64(feature.id.value xor TREAD_SALT)
    private val wanderSeed = GenRng.mix64(seed)
    private val breathSeed = GenRng.mix64(wanderSeed)
    private val fraySeed = GenRng.mix64(breathSeed)
    private val patchSeed = GenRng.mix64(fraySeed)

    fun surfaceAt(worldX: Double, worldY: Double, puddles: Boolean): BlockType? {
      if (!reach.contains(worldX, worldY)) return null

      // The nearest point on the candidate segments, kept by segment and parameter rather than as a `Projection`
      // so the scan allocates nothing.
      var bestDistSq = Double.MAX_VALUE
      var bestSegment = -1
      var bestT = 0.0
      var bestRawT = 0.0
      for (i in segments) {
        val a = points[i]
        val b = points[i + 1]
        val abx = b.x - a.x
        val aby = b.y - a.y
        val raw = ((worldX - a.x) * abx + (worldY - a.y) * aby) / (abx * abx + aby * aby)
        val t = raw.coerceIn(0.0, 1.0)
        val dx = worldX - (a.x + abx * t)
        val dy = worldY - (a.y + aby * t)
        val distSq = dx * dx + dy * dy
        if (distSq < bestDistSq) {
          bestDistSq = distSq
          bestSegment = i
          bestT = t
          bestRawT = raw
        }
      }
      if (bestSegment < 0) return null

      // Off either end of the whole road: it stops there rather than wrapping a round cap of tread.
      if (bestSegment == 0 && bestRawT < 0.0) return null
      if (bestSegment == points.size - 2 && bestRawT > 1.0) return null

      val a = points[bestSegment]
      val b = points[bestSegment + 1]
      val length = sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y))
      val s = feature.centerline.arcLengthAt(bestSegment) + bestT * length
      // Positive to the left of the direction of travel, as `Projection.lateral` is.
      val lateral = ((b.x - a.x) * (worldY - a.y) - (b.y - a.y) * (worldX - a.x)) / length

      val bed = feature.stations.sample(halfWidth, bestSegment + bestT)
      // A bridge gap or a ford: the road stops at the water, and so does its surface.
      if (bed <= 0.0) return null
      if (abs(lateral) > bed) return null

      val half = tread.halfWidth * (1.0 + tread.breathing * along(breathSeed, s, BREATH_WAVELENGTH))
      val wander = tread.wander * along(wanderSeed, s, WANDER_WAVELENGTH)
      val room = max(0.0, bed - half - tread.verge - tread.fray)
      val centre = wander.coerceIn(-room, room)
      val fray = tread.fray * Noise.gradient2d(fraySeed, worldX / FRAY_SCALE, worldY / FRAY_SCALE)

      val off = abs(lateral - centre)
      val edge = half + fray

      return when (roadClass) {
        RoadClass.TRAIL -> if (off <= edge) BlockType.PACKED_EARTH else null

        RoadClass.DIRT_ROAD -> when {
          off > edge -> null
          // A grassy crown between the wheel tracks, where the road is quiet enough for it to grow back.
          off < CROWN_HALF_WIDTH && along(patchSeed, s, CROWN_WAVELENGTH) > CROWN_THRESHOLD -> null
          puddles && abs(off - RUT_OFFSET) < RUT_HALF_WIDTH &&
              Noise.gradient2d(patchSeed, worldX / PUDDLE_SCALE, worldY / PUDDLE_SCALE) > PUDDLE_THRESHOLD ->
            BlockType.MUD
          else -> BlockType.PACKED_EARTH
        }

        RoadClass.COBBLED -> when {
          off <= edge -> BlockType.COBBLESTONE
          off <= edge + tread.verge * (0.6 + 0.4 * Noise.gradient2d(patchSeed, worldX / FRAY_SCALE, worldY / FRAY_SCALE)) ->
            BlockType.PACKED_EARTH
          else -> null
        }
      }
    }

    companion object {

      fun of(feature: PolylineFeature, bounds: Aabb): Road? {
        val roadClass = RoadClass.of(feature) ?: return null
        val stations = feature.stations
        if (Profiles.CHANNEL_HALF_WIDTH !in stations.channelNames) return null
        val halfWidth = stations.channel(Profiles.CHANNEL_HALF_WIDTH)

        val points = feature.centerline.points
        val picked = ArrayList<Int>()
        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE

        for (i in 0 until points.size - 1) {
          val bed = max(stations.valueAt(halfWidth, i), stations.valueAt(halfWidth, i + 1))
          // Margin for the spline overshooting between stations, which a road's smooth width barely does.
          val margin = bed * 1.25 + 1.0
          val a = points[i]
          val b = points[i + 1]
          val box = Aabb(
            minOf(a.x, b.x) - margin, minOf(a.y, b.y) - margin,
            maxOf(a.x, b.x) + margin, maxOf(a.y, b.y) + margin
          )
          if (!box.intersects(bounds)) continue

          picked.add(i)
          minX = minOf(minX, box.minX)
          minY = minOf(minY, box.minY)
          maxX = maxOf(maxX, box.maxX)
          maxY = maxOf(maxY, box.maxY)
        }
        if (picked.isEmpty()) return null

        return Road(feature, roadClass, halfWidth, picked.toIntArray(), Aabb(minX, minY, maxX, maxY))
      }

      /** Smooth noise along the road in `[-1, 1]`, read off the lattice row for `SettlementStage`'s reason. */
      private fun along(seed: Long, s: Double, wavelength: Double) =
        Noise.gradient2d(seed, s / wavelength, LATTICE_OFFSET)
    }
  }

  /**
   * How a class's tread is drawn, in metres. Chunk-tier tuning, so it lives here rather than on [RoadClass] and
   * moves `ChunkMaterializer.VERSION` rather than the world tier when it changes.
   */
  private class Tread(
    /** Half the tread at its mean width. */
    val halfWidth: Double,
    /** How far that varies along the road, as a share of it. */
    val breathing: Double,
    /** How far the tread's middle strays from the centreline. */
    val wander: Double,
    /** How far the edge is ragged either way. */
    val fray: Double,
    /** Beaten earth beside the stone, for a paved road. */
    val verge: Double
  ) {
    companion object {
      private val TRAIL = Tread(halfWidth = 1.3, breathing = 0.55, wander = 2.5, fray = 0.6, verge = 0.0)
      private val DIRT_ROAD = Tread(halfWidth = 3.0, breathing = 0.3, wander = 1.5, fray = 0.7, verge = 0.0)
      private val COBBLED = Tread(halfWidth = 3.2, breathing = 0.05, wander = 0.4, fray = 0.15, verge = 1.4)

      fun of(roadClass: RoadClass) = when (roadClass) {
        RoadClass.TRAIL -> TRAIL
        RoadClass.DIRT_ROAD -> DIRT_ROAD
        RoadClass.COBBLED -> COBBLED
      }
    }
  }

  companion object {

    /** Biomes where a dirt road is dust rather than mud: no puddles stand in its ruts. */
    private val ARID = setOf(
      Biome.DESERT, Biome.COLD_DESERT, Biome.DRYLAND, Biome.BADLANDS, Biome.BEACH, Biome.VOLCANIC_FIELD
    )

    /** Whether a dirt road here holds water in its ruts. Below freezing they are hard ridges, not puddles. */
    fun puddlesIn(biome: Biome, temperature: Double, freezing: Double): Boolean =
      biome !in ARID && temperature >= freezing

    private const val TREAD_SALT = 0x7472656164L

    /** See `SettlementStage.LATTICE_OFFSET`: gradient noise is zero along integer rows. */
    private const val LATTICE_OFFSET = 0.5

    /** Metres between one swing of the tread and the next. Short, because this is the wobble underfoot. */
    private const val WANDER_WAVELENGTH = 70.0

    /** Metres between one widening of the tread and the next. */
    private const val BREATH_WAVELENGTH = 90.0

    /** Metres across one rag of a tread's edge. */
    private const val FRAY_SCALE = 2.5

    /** A dirt road's wheel ruts: how far each runs from the tread's middle, and how wide. */
    private const val RUT_OFFSET = 1.2
    private const val RUT_HALF_WIDTH = 0.45

    /** Metres across one puddle, and how much of a rut is under water: noise above this. */
    private const val PUDDLE_SCALE = 6.0
    private const val PUDDLE_THRESHOLD = 0.35

    /** The grass crown down a quiet dirt road: how wide, how long a stretch, and how often. */
    private const val CROWN_HALF_WIDTH = 0.5
    private const val CROWN_WAVELENGTH = 160.0
    private const val CROWN_THRESHOLD = 0.45
  }
}
