package net.bestia.worldgen.geo

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.bio.BiomeStage
import net.bestia.worldgen.civ.SettlementStage
import net.bestia.worldgen.civ.TownStage
import net.bestia.worldgen.coast.CoastStage
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FeatureIds
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.fields.Noise
import net.bestia.worldgen.history.HistoryStage
import net.bestia.worldgen.hydro.AlluviumStage
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.hydro.PondStage
import net.bestia.worldgen.karst.CaveStage
import net.bestia.worldgen.poi.PoiStage
import net.bestia.worldgen.pop.EconomyStage
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.BlendMode
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.FootprintFeature
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.PointFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.RadialProfiles
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Tuning for [KnollStage]. */
data class KnollParams(

  /**
   * Metres between the lattice points a hill cluster may start from.
   *
   * One cluster at most per lattice cell, so this and [clusterChance] together are how many clusters there are.
   */
  val clusterSpacing: Double = 450.0,

  /** Chance a lattice cell inside full hill country starts a cluster, before biome and relief weights. */
  val clusterChance: Double = 0.55,

  /** Fewest and most hills in one cluster. A cluster is a short wandering chain, so it reads as a ridge. */
  val minHillsPerCluster: Int = 2,
  val maxHillsPerCluster: Int = 6,

  /** Range of a single hill's radius, in metres. Drawn per cluster, then varied per hill. */
  val minRadius: Double = 40.0,
  val maxRadius: Double = 180.0,

  /**
   * Range of a hill's height as a share of its radius.
   *
   * Capped low on purpose: the smoothstep cone's steepest flank is 1.5 times this, so at 0.2 a single hill tops
   * out near seventeen degrees, and two overlapping in a cluster stay walkable under the 45 degree rule.
   */
  val minHeightRatio: Double = 0.08,
  val maxHeightRatio: Double = 0.2,

  /** Tallest hill, in metres, whatever its radius gives. */
  val maxHeight: Double = 25.0,

  /** Hills lower than this after every reduction are dropped rather than left as a bump. */
  val minHeight: Double = 2.5,

  /** Wavelength of the "hill country" field, in metres: how far across a district of hills is. */
  val countryWavelength: Double = 9_000.0,

  /** Value of that field below which there are no hills at all. Higher means less of the world is hill country. */
  val countryCutoff: Double = 0.44,

  /** Width of the ramp above [countryCutoff], so hill country fades in rather than starting at a line. */
  val countrySoftness: Double = 0.1,

  /** Coarse slope (rise over run) the relief weight peaks at: foothills. */
  val foothillSlope: Double = 0.04,

  /** Coarse slope above which there are no hills: mountains carry their own ridge noise already. */
  val maxSlope: Double = 0.16,

  /** Metres above sea level a hill's centre must stand, so the coast keeps its beaches. */
  val freeboard: Double = 6.0,

  /** Metres a hill's foot keeps from a river's floodplain, a lake shore, a field or anything built. */
  val clearance: Double = 20.0,

  /** Metres a hill keeps from a history site, whose structure stands at a stored elevation. */
  val siteClearance: Double = 120.0,

  /** Metres a hill's foot keeps from a road corridor before it is slid aside or lowered. */
  val roadMargin: Double = 10.0,

  /**
   * Slope of the cutting where a road passes through a hill's flank, as rise over the road's shoulder width.
   *
   * A hill straddling a road is lowered until its height at the corridor edge, spread over the shoulder, is at
   * most this. tan 35 degrees: a bank a player can scramble up.
   */
  val maxCuttingSlope: Double = 0.7
) : Params {

  init {
    require(clusterSpacing > 0.0) { "clusterSpacing must be positive, was $clusterSpacing" }
    require(clusterChance in 0.0..1.0) { "clusterChance must be in [0,1], was $clusterChance" }
    require(minHillsPerCluster in 1..maxHillsPerCluster) { "hills per cluster range is inverted" }
    require(minRadius > 0.0 && maxRadius >= minRadius) { "radius range is inverted" }
    require(minHeightRatio > 0.0 && maxHeightRatio >= minHeightRatio) { "height ratio range is inverted" }
    require(maxHeight > 0.0 && minHeight >= 0.0) { "height bounds must be positive" }
    require(countryWavelength > 0.0 && countrySoftness > 0.0) { "country field must have a positive scale" }
    require(foothillSlope > 0.0 && maxSlope > foothillSlope) { "slope weights are inverted" }
    require(clearance >= 0.0 && siteClearance >= 0.0 && roadMargin >= 0.0) { "clearances must not be negative" }
    require(maxCuttingSlope > 0.0) { "maxCuttingSlope must be positive, was $maxCuttingSlope" }
  }

  override fun digest() = ParamsDigest()
    .put("clusterSpacing", clusterSpacing)
    .put("clusterChance", clusterChance)
    .put("minHillsPerCluster", minHillsPerCluster)
    .put("maxHillsPerCluster", maxHillsPerCluster)
    .put("minRadius", minRadius)
    .put("maxRadius", maxRadius)
    .put("minHeightRatio", minHeightRatio)
    .put("maxHeightRatio", maxHeightRatio)
    .put("maxHeight", maxHeight)
    .put("minHeight", minHeight)
    .put("countryWavelength", countryWavelength)
    .put("countryCutoff", countryCutoff)
    .put("countrySoftness", countrySoftness)
    .put("foothillSlope", foothillSlope)
    .put("maxSlope", maxSlope)
    .put("freeboard", freeboard)
    .put("clearance", clearance)
    .put("siteClearance", siteClearance)
    .put("roadMargin", roadMargin)
    .put("maxCuttingSlope", maxCuttingSlope)
}

/**
 * Hills woven into the open country: clusters of low rounded knolls a few hundred metres across.
 *
 * The kilometre heightfield cannot hold a hill this size and the chunk tier's detail noise only roughens what is
 * there; neither gives the rolling ground of a landscape that has been lived in, where a road threads between
 * rises and a village sits in the fold of two. These are vector features for the reason a river is: a hill is a
 * shape with a centre and a size, and every chunk it touches must agree on it to the millimetre.
 *
 * ### Hills go round the ways, not the ways round the hills
 *
 * The one design decision here, and it is forced. Roads are routed over kilometre cells, so no road router can
 * see a hill a fifth of a cell across - asking roads to avoid hills would mean routing at a resolution the whole
 * network was never planned at. So this stage runs **after every way, field, building and site exists**, and the
 * ways decide where the hills may stand:
 *
 * - a hill keeps clear of streets, buildings, settlement grading, fields, river floodplains, lakes, history sites,
 *   points of interest, cave mouths, inns and bridges outright;
 * - a hill whose foot reaches a road corridor is first **slid away from the road**, up to one radius - which is
 *   what puts a road between two rises rather than across one;
 * - one that still reaches it is **lowered** until the cutting the road makes through its flank is a bank a
 *   player can climb. The road's own `REPLACE` profile then flattens its tread through the hill, because a road
 *   stamps above a hill and its tread height was decided before hills existed.
 *
 * ### `ADD`, and why that is safe here
 *
 * Each hill is a smoothstep cone piled on the ground, blended `ADD` at priority between the river cuts and the
 * roads. `GlacialStage.carveInto` rasterises only `MIN` features, so a hill is never applied twice; and nothing
 * after this stage reads the kilometre `ELEVATION` raster expecting to find a hill in it - this runs last but for
 * the nav graph, which routes at kilometre scale and is right not to.
 *
 * The map's hillshade reads that raster too, so hills do not show on the map. That is accepted: they are a thing
 * to walk among, not a thing to navigate by.
 */
class KnollStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: KnollParams = KnollParams()
) : Stage {

  override val id = ID

  override val version = 1

  override val paramsVersion get() = params.digest().value

  /**
   * Everything a hill must keep clear of, which is nearly everything built. See the class note.
   *
   * `TownStage` for streets, buildings, fields and walls; `EconomyStage` for roadside inns; `PoiStage` and
   * `HistoryStage` for what stands at a stored elevation; `CaveStage` for mouths; the three water stages for
   * floodplains, lakes, deltas and fans; `CoastStage` for the beaches; `SettlementStage` for roads and grading.
   */
  override val dependencies = listOf(
    GlacialStage.ID,
    HydrologyStage.ID,
    PondStage.ID,
    AlluviumStage.ID,
    BiomeStage.ID,
    CoastStage.ID,
    CaveStage.ID,
    SettlementStage.ID,
    HistoryStage.ID,
    TownStage.ID,
    EconomyStage.ID,
    PoiStage.ID
  )

  override val scale = StageScale.WORLD

  override val outputs = listOf(StageOutput.Vector(FeatureKind.KNOLL))

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val elevation = ctx.layers.float(LayerId.ELEVATION)
    val waterLevel = ctx.layers.float(LayerId.WATER_LEVEL)
    val biome = ctx.layers.int(LayerId.BIOME)
    val metres = region.resolution.metresPerCell
    val seaLevel = ctx.config.seaLevel

    val world = region.toWorld()
    val countrySeed = GenRng.hash(ctx.seed, id.hash, COUNTRY_SALT)
    val clusterSeed = GenRng.hash(ctx.seed, id.hash, CLUSTER_SALT)

    val nextId = FeatureIds.allocator(id)
    val out = ArrayList<VectorFeature>()

    val spacing = params.clusterSpacing
    val fromX = Math.floorDiv(world.minX.toLong(), spacing.toLong())
    val untilX = Math.floorDiv(world.maxX.toLong(), spacing.toLong())
    val fromY = Math.floorDiv(world.minY.toLong(), spacing.toLong())
    val untilY = Math.floorDiv(world.maxY.toLong(), spacing.toLong())

    for (cellY in fromY until untilY) {
      for (cellX in fromX until untilX) {
        val key = GenRng.hash(clusterSeed, cellX, cellY)
        val roll = GenRng.unit(key)
        if (roll >= params.clusterChance) continue

        val startX = (cellX + GenRng.unit(GenRng.mix64(key + 1))) * spacing
        val startY = (cellY + GenRng.unit(GenRng.mix64(key + 2))) * spacing
        if (!world.contains(startX, startY)) continue

        val weight = countryAt(countrySeed, startX, startY) *
            groundWeight(elevation, waterLevel, biome, metres, seaLevel, startX, startY)
        if (roll >= params.clusterChance * weight) continue

        cluster(ctx, key, Vec2d(startX, startY), elevation, waterLevel, seaLevel, nextId, out)
      }
    }

    return StageResult(features = out)
  }

  /** Lays one wandering chain of hills from [start], appending the ones that find room to [out]. */
  private fun cluster(
    ctx: GenContext,
    key: Long,
    start: Vec2d,
    elevation: FloatLayer,
    waterLevel: FloatLayer,
    seaLevel: Double,
    nextId: () -> net.bestia.worldgen.vector.FeatureId,
    out: MutableList<VectorFeature>
  ) {
    var h = GenRng.mix64(key + 3)
    fun next(): Double {
      h = GenRng.mix64(h + 1)
      return GenRng.unit(h)
    }

    val count = params.minHillsPerCluster +
        (next() * (params.maxHillsPerCluster - params.minHillsPerCluster + 1)).toInt()
            .coerceAtMost(params.maxHillsPerCluster - params.minHillsPerCluster)
    val baseRadius = params.minRadius + (params.maxRadius - params.minRadius) * next() * next()
    val ratio = params.minHeightRatio + (params.maxHeightRatio - params.minHeightRatio) * next()
    var heading = next() * 2.0 * PI
    var at = start

    for (k in 0 until count) {
      val radius = (baseRadius * (0.7 + 0.6 * next())).coerceIn(params.minRadius, params.maxRadius)
      val height = min(params.maxHeight, radius * ratio * (0.75 + 0.5 * next()))

      hillAt(ctx, at, radius, height, elevation, waterLevel, seaLevel)?.let { (centre, finalHeight) ->
        out.add(
          PointFeature(
            id = nextId(),
            kind = FeatureKind.KNOLL,
            center = centre,
            radius = radius,
            profile = RadialProfiles.cone(finalHeight, radius),
            edgeFraction = 0.0,
            blend = BlendMode.ADD
          )
        )
      }

      // The next hill along the chain: a little more than a radius on, the heading drifting so the chain bends.
      heading += (next() - 0.5) * 1.4
      val step = radius * (0.9 + 0.6 * next())
      at = Vec2d(at.x + cos(heading) * step, at.y + sin(heading) * step)
    }
  }

  /**
   * Where one hill may stand and how tall, or null if it finds no room.
   *
   * Water and freeboard first because they are raster reads, then the keep-outs, then the road rule, which may
   * move the hill - and a moved hill is re-checked against everything, because sliding off a road can slide it
   * onto a field.
   */
  private fun hillAt(
    ctx: GenContext,
    centre: Vec2d,
    radius: Double,
    height: Double,
    elevation: FloatLayer,
    waterLevel: FloatLayer,
    seaLevel: Double
  ): Pair<Vec2d, Double>? {
    if (!isDryAndAbove(centre, elevation, waterLevel, seaLevel)) return null

    val reach = radius + params.siteClearance + ROAD_CORRIDOR_ALLOWANCE
    val nearby = ctx.features.query(Aabb(centre.x - reach, centre.y - reach, centre.x + reach, centre.y + reach))
    if (isBlocked(centre, radius, nearby)) return null

    val road = nearestRoad(centre, nearby) ?: return centre to height
    val needed = radius + road.corridor + params.roadMargin
    if (road.distance >= needed) return centre to height

    // Slide away from the road, along the line from the road to the hill, by as much as it lacks - up to a radius.
    val lacking = needed - road.distance
    if (lacking <= radius && road.distance > 1e-6) {
      val away = (centre - road.point) * (1.0 / road.distance)
      val moved = centre + away * lacking
      if (isDryAndAbove(moved, elevation, waterLevel, seaLevel)) {
        val movedNearby = ctx.features.query(
          Aabb(moved.x - reach, moved.y - reach, moved.x + reach, moved.y + reach)
        )
        if (!isBlocked(moved, radius, movedNearby)) {
          val after = nearestRoad(moved, movedNearby)
          if (after == null || after.distance >= radius + after.corridor + params.roadMargin - SLIDE_TOLERANCE) {
            return moved to height
          }
        }
      }
    }

    // Lowered instead: the cone's height at the corridor edge, spread over the shoulder, may not exceed the
    // cutting slope. The cone is `h * smoothstep(1 - d / r)`, so solve for `h` at the edge's distance.
    val edge = (road.distance - road.corridor).coerceAtLeast(0.0)
    if (edge >= radius) return centre to height
    val shape = smoothstep(1.0 - edge / radius)
    val allowed = params.maxCuttingSlope * road.shoulder
    val lowered = if (shape <= 0.0) height else min(height, allowed / shape)
    return if (lowered < params.minHeight) null else centre to lowered
  }

  private fun isDryAndAbove(at: Vec2d, elevation: FloatLayer, waterLevel: FloatLayer, seaLevel: Double): Boolean {
    if (elevation.sampleBilinear(at.x, at.y) - seaLevel < params.freeboard) return false
    return waterLevel.sampleBilinear(at.x, at.y).isNaN()
  }

  /** Whether anything a hill must keep clear of lies within its foot plus a margin. */
  private fun isBlocked(centre: Vec2d, radius: Double, nearby: List<VectorFeature>): Boolean {
    val foot = radius + params.clearance
    for (feature in nearby) {
      val blocked = when (feature.kind) {
        in CORRIDORS -> {
          val line = (feature as? PolylineFeature)?.centerline ?: continue
          line.project(centre).distance < foot + feature.corridorWidthMax
        }
        in AREAS -> {
          val area = feature as? AreaFeature ?: continue
          area.bbox.intersects(Aabb(centre.x - foot, centre.y - foot, centre.x + foot, centre.y + foot)) &&
              (area.contains(centre.x, centre.y) || area.ring.project(centre).distance < foot)
        }
        in DISCS -> {
          val disc = feature as? PointFeature ?: continue
          disc.center.distanceTo(centre) < foot + disc.radius
        }
        FeatureKind.BUILDING -> {
          val building = feature as? FootprintFeature ?: continue
          building.center.distanceTo(centre) < foot + building.halfLength
        }
        FeatureKind.COASTLINE -> {
          val coast = feature as? MarkerFeature ?: continue
          coast.centerline.project(centre).distance < foot + COAST_CLEARANCE
        }
        in SITES -> {
          val site = feature as? PointMarker ?: continue
          site.position.distanceTo(centre) < radius + params.siteClearance
        }
        in POLYLINE_SITES -> {
          val line = when (feature) {
            is PolylineFeature -> feature.centerline
            is MarkerFeature -> feature.centerline
            else -> continue
          }
          line.project(centre).distance < foot
        }
        else -> false
      }
      if (blocked) return true
    }
    return false
  }

  private class NearRoad(val distance: Double, val point: Vec2d, val corridor: Double, val shoulder: Double)

  /** The road nearest [centre] among [nearby], with its corridor half-width and its shoulder width. */
  private fun nearestRoad(centre: Vec2d, nearby: List<VectorFeature>): NearRoad? {
    var best: NearRoad? = null
    for (feature in nearby) {
      if (feature.kind != FeatureKind.ROAD) continue
      val road = feature as? PolylineFeature ?: continue
      val projection = road.centerline.project(centre)
      if (best == null || projection.distance < best.distance) {
        val corridor = road.corridorWidthMax
        best = NearRoad(projection.distance, projection.point, corridor, max(MIN_SHOULDER, corridor * SHOULDER_SHARE))
      }
    }
    return best
  }

  /** The hill-country field in `[0, 1]`: zero outside a district of hills, ramping to one inside it. */
  private fun countryAt(seed: Long, x: Double, y: Double): Double {
    val raw = (Noise.fbm(seed, x / params.countryWavelength, y / params.countryWavelength, COUNTRY_OCTAVES) + 1.0) * 0.5
    return smoothstep((raw - params.countryCutoff) / params.countrySoftness)
  }

  /** How much the ground under a point wants hills, in `[0, 1]`, from its biome and its coarse slope. */
  private fun groundWeight(
    elevation: FloatLayer,
    waterLevel: FloatLayer,
    biome: IntLayer,
    metres: Double,
    seaLevel: Double,
    x: Double,
    y: Double
  ): Double {
    val cellX = Math.floor(x / metres).toInt()
    val cellY = Math.floor(y / metres).toInt()
    val here = Biome.entries[biome[cellX, cellY]]
    val biomeWeight = biomeWeightOf(here)
    if (biomeWeight <= 0.0) return 0.0
    if (!waterLevel[cellX, cellY].isNaN()) return 0.0

    val dx = (elevation[cellX + 1, cellY] - elevation[cellX - 1, cellY]) / (2.0 * metres)
    val dy = (elevation[cellX, cellY + 1] - elevation[cellX, cellY - 1]) / (2.0 * metres)
    val slope = sqrt(dx * dx + dy * dy)
    if (slope >= params.maxSlope) return 0.0
    if (elevation[cellX, cellY] - seaLevel < params.freeboard) return 0.0

    // Rising to full weight at the foothill slope and falling back to nothing at the mountain slope. Flat ground
    // keeps half, because a plain has its drumlins and swells too.
    val relief = if (slope <= params.foothillSlope) {
      0.5 + 0.5 * slope / params.foothillSlope
    } else {
      1.0 - (slope - params.foothillSlope) / (params.maxSlope - params.foothillSlope)
    }
    return biomeWeight * relief.coerceIn(0.0, 1.0)
  }

  private fun biomeWeightOf(biome: Biome): Double = when (biome) {
    Biome.DRYLAND, Biome.GRASSLAND -> 1.0
    Biome.BADLANDS -> 0.9
    Biome.TUNDRA, Biome.DESERT, Biome.COLD_DESERT -> 0.6
    Biome.TEMPERATE_FOREST, Biome.TROPICAL_SEASONAL_FOREST, Biome.TAIGA -> 0.5
    Biome.ALPINE, Biome.TEMPERATE_RAINFOREST, Biome.TROPICAL_RAINFOREST, Biome.GEOTHERMAL_BASIN -> 0.3
    else -> 0.0
  }

  private fun smoothstep(t: Double): Double {
    val c = t.coerceIn(0.0, 1.0)
    return c * c * (3.0 - 2.0 * c)
  }

  companion object {

    val ID = StageId("knolls")

    /** Long features whose corridor a hill keeps clear of. */
    private val CORRIDORS = setOf(FeatureKind.RIVER_CHANNEL, FeatureKind.STREET)

    /** Areas a hill keeps clear of: standing water, worked ground, quarters of a town. */
    private val AREAS = setOf(
      FeatureKind.LAKE,
      FeatureKind.OXBOW_LAKE,
      FeatureKind.DELTA,
      FeatureKind.ALLUVIAL_FAN,
      FeatureKind.FIELD,
      FeatureKind.DISTRICT,
      FeatureKind.LAVA_POOL
    )

    /** Discs a hill keeps clear of: graded settlements and river junctions. */
    private val DISCS = setOf(FeatureKind.SETTLEMENT_GRADING, FeatureKind.RIVER_CONFLUENCE, FeatureKind.CIRQUE)

    /** Point sites whose structure, or whose prop, stands at an elevation decided before hills existed. */
    private val SITES = setOf(
      FeatureKind.RUIN,
      FeatureKind.ASH_RUIN,
      FeatureKind.BATTLEFIELD,
      FeatureKind.MONUMENT,
      FeatureKind.TOMB,
      FeatureKind.MINE,
      FeatureKind.MONASTERY,
      FeatureKind.FORT,
      FeatureKind.LIGHTHOUSE,
      FeatureKind.SHRINE,
      FeatureKind.SANCTUARY,
      FeatureKind.SEAL,
      FeatureKind.LAIR,
      FeatureKind.WOUND,
      FeatureKind.POI,
      FeatureKind.CAVE_ENTRANCE,
      FeatureKind.BRIDGE,
      FeatureKind.ROADSIDE_INN,
      FeatureKind.GATE,
      FeatureKind.SETTLEMENT
    )

    /** Linear structures a hill keeps clear of that are not corridors with a width. */
    private val POLYLINE_SITES = setOf(FeatureKind.TOWN_WALL)

    /** Metres a hill keeps from the shoreline, beyond its foot. A beach is a few tens of metres wide. */
    private const val COAST_CLEARANCE = 60.0

    /** Widest road corridor the query has to reach, so a hill beside a cobbled trunk road finds it. */
    private const val ROAD_CORRIDOR_ALLOWANCE = 40.0

    /** A road's shoulder as a share of its corridor half-width, and its floor, in metres. See `RoadClass`. */
    private const val SHOULDER_SHARE = 0.65
    private const val MIN_SHOULDER = 8.0

    /** Metres a slid hill may still overlap the road margin by, for the projection moving under it. */
    private const val SLIDE_TOLERANCE = 2.0

    private const val COUNTRY_OCTAVES = 3
    private const val COUNTRY_SALT = 0x4B4E4F4C4CL
    private const val CLUSTER_SALT = 0x434C55535452L
  }
}
