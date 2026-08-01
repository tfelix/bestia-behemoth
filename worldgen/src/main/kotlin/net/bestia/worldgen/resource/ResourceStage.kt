package net.bestia.worldgen.resource

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.bio.BiomeStage
import net.bestia.worldgen.climate.ClimateStage
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FeatureIds
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
import net.bestia.worldgen.fields.D8
import net.bestia.worldgen.fields.DistanceTransform
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.fields.PoissonDisk
import net.bestia.worldgen.geo.BoundaryType
import net.bestia.worldgen.geo.ErosionStage
import net.bestia.worldgen.geo.TectonicsStage
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Tuning for [ResourceStage]. */
data class ResourceParams(

  /**
   * Target spacing between candidate sites of one resource type, in metres.
   *
   * Candidates are then *thinned* by the suitability field, so this sets the finest possible spacing rather
   * than the actual density: a type whose geology only occurs in one corner of the world ends up with
   * deposits only there, however small this is.
   */
  val candidateSpacing: Double = 34_000.0,

  /** Radius over which a deposit contributes to the resource-value field, in metres. */
  val valueRadius: Double = 42_000.0,

  /** How far placer gold is carried downstream from a lode, in metres. */
  val placerRange: Double = 90_000.0,

  /** Spacing between placer deposits along a river, in metres. */
  val placerSpacing: Double = 12_000.0
) : Params {

  init {
    require(candidateSpacing > 0.0) { "candidateSpacing must be positive, was $candidateSpacing" }
    require(valueRadius > 0.0) { "valueRadius must be positive, was $valueRadius" }
    require(placerRange >= 0.0) { "placerRange must not be negative, was $placerRange" }
    require(placerSpacing > 0.0) { "placerSpacing must be positive, was $placerSpacing" }
  }

  override fun digest() = ParamsDigest()
    .put("candidateSpacing", candidateSpacing)
    .put("valueRadius", valueRadius)
    .put("placerRange", placerRange)
    .put("placerSpacing", placerSpacing)
}

/**
 * Stage 6: mineral and surface resources, placed causally.
 *
 * Resources are *not* sprinkled. Every type here has a geological or ecological setting, and it is read
 * from what earlier stages already established rather than invented: arc metals from the convergent plate
 * boundaries the tectonics stage emitted as fault polylines, tin from old hard crust, coal from wet
 * sedimentary basins, salt from the beds of endorheic lakes the hydrology stage identified, clay from
 * floodplain sediment, placer gold traced *downstream* of hard-rock gold along the river network.
 *
 * That last one is worth the effort on its own: it makes prospecting a real mechanic. A player who finds
 * placer gold in a river gravel can walk upstream and find the lode, because the world actually put it
 * there for that reason.
 *
 * Deposits are emitted as sparse [PointMarker] features, never as a per-cell field. A world has perhaps a
 * hundred thousand of them against sixteen million cells; per-voxel ore materialises at chunk generation by
 * sampling the deposit, and is never stored.
 */
class ResourceStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: ResourceParams = ResourceParams()
) : Stage {

  override val id = ID
  override val version = 1

  override val paramsVersion get() = GenRng.hash(params.digest().value, ResourceType.catalogueDigest())
  override val dependencies = listOf(
    TectonicsStage.ID, ClimateStage.ID, ErosionStage.ID, HydrologyStage.ID, BiomeStage.ID
  )
  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Raster(LayerId.RESOURCE_VALUE),
    StageOutput.Vector(FeatureKind.ORE_DEPOSIT)
  )

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val terrain = Terrain.read(ctx, region)
    val nextId = FeatureIds.allocator(id)
    val deposits = ArrayList<PointMarker>()

    // Fixed iteration order over the enum, so ids and therefore blend order are reproducible.
    for (type in ResourceType.entries) {
      val suitability = terrain.suitabilityFor(type)
      val spacing = params.candidateSpacing * spacingFactorOf(type)
      val rng = ctx.rng(CANDIDATE_STREAM, type.ordinal.toLong())

      for (candidate in PoissonDisk.sample(terrain.bounds, spacing, rng)) {
        val score = suitability(candidate)
        // Thinning a Poisson process by an acceptance probability yields a Poisson process with the
        // suitability as its intensity - which is exactly the model the architecture document asks for, and
        // it needs no rejection loop or density normalisation.
        if (score <= 0.0 || rng.nextDouble() > score) continue

        deposits.add(
          marker(
            id = nextId(),
            position = candidate,
            type = type,
            richness = (0.35 + score * 0.65) * (0.7 + rng.nextDouble() * 0.6),
            quantity = (200.0 + rng.nextDouble() * 1800.0) * score,
            depth = depthFor(type, rng.nextDouble())
          )
        )
      }
    }

    deposits.addAll(tracePlacers(ctx, terrain, deposits.toList(), nextId))

    return StageResult(
      layers = listOf(terrain.valueField(deposits, params.valueRadius).toLayer(LayerId.RESOURCE_VALUE, region)),
      features = deposits
    )
  }

  private fun marker(
    id: net.bestia.worldgen.vector.FeatureId,
    position: Vec2d,
    type: ResourceType,
    richness: Double,
    quantity: Double,
    depth: Double
  ) = PointMarker(
    id = id,
    kind = FeatureKind.ORE_DEPOSIT,
    position = position,
    attributes = StationTable.Builder(1)
      .channel(DepositChannels.TYPE) { type.ordinal.toDouble() }
      .channel(DepositChannels.RICHNESS) { richness.coerceIn(0.0, 1.0) }
      .channel(DepositChannels.QUANTITY) { quantity }
      .channel(DepositChannels.DEPTH) { depth }
      // Derived from quantity rather than drawn separately, so a big deposit is a big orebody and the two
      // numbers cannot contradict each other.
      .channel(DepositChannels.RADIUS) { radiusFor(quantity) }
      .build()
  )

  /** Horizontal extent of an orebody holding [quantity] units. Cube root, because a body is a volume. */
  private fun radiusFor(quantity: Double): Double =
    (MIN_ORE_RADIUS + Math.cbrt(quantity.coerceAtLeast(0.0)) * ORE_RADIUS_FACTOR)

  /**
   * Placer gold: the gravels downstream of a hard-rock gold deposit.
   *
   * Walks the D8 flow network from each lode and drops deposits at intervals with richness decaying by
   * distance - which is what a river actually does to eroded gold. The vector river graph is not even
   * needed for this; the raster flow directions are enough, and following them is a loop.
   */
  private fun tracePlacers(
    ctx: GenContext,
    terrain: Terrain,
    lodes: List<PointMarker>,
    nextId: () -> net.bestia.worldgen.vector.FeatureId
  ): List<PointMarker> {
    val out = ArrayList<PointMarker>()
    val typeChannel = DepositChannels.TYPE
    val rng = ctx.rng(PLACER_STREAM)

    for (lode in lodes) {
      if (lode.attribute(typeChannel).toInt() != ResourceType.GOLD_LODE.ordinal) continue

      val lodeRichness = lode.attribute(DepositChannels.RICHNESS)
      var cell = terrain.cellAt(lode.position) ?: continue
      var travelled = 0.0
      var sinceLast = 0.0

      while (travelled < params.placerRange) {
        val direction = terrain.flowDirection.data[cell]
        if (direction == D8.NONE) break

        val step = D8.LENGTH[direction] * terrain.metresPerCell
        travelled += step
        sinceLast += step
        cell = terrain.step(cell, direction) ?: break

        // Placers form in gravel bars, so only where there is actually a river and it is not underwater.
        if (terrain.discharge.data[cell] < PLACER_MIN_DISCHARGE) continue
        if (!terrain.waterLevel.data[cell].isNaN()) break

        if (sinceLast < params.placerSpacing) continue
        sinceLast = 0.0

        val decay = exp(-travelled / (params.placerRange * 0.45))
        out.add(
          marker(
            id = nextId(),
            position = terrain.centreOf(cell),
            type = ResourceType.GOLD_PLACER,
            richness = lodeRichness * decay * (0.6 + rng.nextDouble() * 0.5),
            quantity = 120.0 * decay * (0.5 + rng.nextDouble()),
            // In the gravels, which is why it is panned rather than mined.
            depth = rng.nextDouble() * 3.0
          )
        )
      }
    }

    return out
  }

  /**
   * How much rarer than the baseline each type's candidate sites are.
   *
   * Precious metals are scarce, building stone is not. Setting scarcity per type here rather than folding it
   * into the suitability field keeps the two questions separate: suitability answers "could it be here",
   * scarcity answers "how often".
   */
  private fun spacingFactorOf(type: ResourceType): Double = when (type) {
    ResourceType.GOLD_LODE, ResourceType.SILVER -> 2.4
    ResourceType.MARBLE, ResourceType.TIN -> 1.7
    ResourceType.COPPER, ResourceType.IRON, ResourceType.COAL, ResourceType.SALT -> 1.2
    ResourceType.STONE, ResourceType.CLAY, ResourceType.TIMBER, ResourceType.FISH -> 0.75
    ResourceType.FURS -> 1.0
    ResourceType.GOLD_PLACER -> 1.0
  }

  /** Metres below the surface. Surface resources are at zero; a deep seam needs a shaft. */
  private fun depthFor(type: ResourceType, roll: Double): Double = when (type) {
    ResourceType.TIMBER, ResourceType.FURS, ResourceType.FISH, ResourceType.STONE -> 0.0
    ResourceType.CLAY, ResourceType.SALT, ResourceType.GOLD_PLACER -> roll * 6.0
    ResourceType.COAL -> 20.0 + roll * 180.0
    else -> 10.0 + roll * 140.0
  }

  companion object {
    val ID = StageId("resources")

    private const val CANDIDATE_STREAM = 1L
    private const val PLACER_STREAM = 2L

    /** Discharge in cubic metres per second below which a stream carries no worthwhile gravel bar. */
    private const val PLACER_MIN_DISCHARGE = 2.0

    /** Smallest orebody worth finding, in metres. */
    private const val MIN_ORE_RADIUS = 14.0

    private const val ORE_RADIUS_FACTOR = 5.5
  }
}

/**
 * The raster context resource placement reads: the layers, plus a couple of derived distance fields.
 *
 * A separate type because the suitability rules are the interesting part and threading a dozen grids
 * through each of them would bury them. Everything here is read once at stage entry.
 */
private class Terrain(
  val region: CellRegion,
  val metresPerCell: Double,
  val seaLevel: Double,
  val elevation: Grid,
  val hardness: Grid,
  val crustAge: Grid,
  val sediment: Grid,
  val precipitation: Grid,
  val discharge: Grid,
  val waterLevel: Grid,
  val biome: IntLayer,
  val lakeId: IntLayer,
  val flowDirection: IntLayer,
  /** Metres to the nearest convergent plate boundary. */
  val toConvergent: Grid
) {

  val bounds = region.toWorld()

  fun cellAt(position: Vec2d): Int? {
    val x = (position.x / metresPerCell).toInt() - region.minX
    val y = (position.y / metresPerCell).toInt() - region.minY
    if (x < 0 || y < 0 || x >= region.width || y >= region.height) return null
    return y * region.width + x
  }

  fun centreOf(cell: Int) = Vec2d(
    (region.minX + cell % region.width + 0.5) * metresPerCell,
    (region.minY + cell / region.width + 0.5) * metresPerCell
  )

  fun step(cell: Int, direction: Int): Int? {
    val x = cell % region.width + D8.DX[direction]
    val y = cell / region.width + D8.DY[direction]
    if (x < 0 || y < 0 || x >= region.width || y >= region.height) return null
    return y * region.width + x
  }

  private fun biomeAt(cell: Int) =
    Biome.of(biome[region.minX + cell % region.width, region.minY + cell / region.width])

  private fun lakeAt(cell: Int) =
    lakeId[region.minX + cell % region.width, region.minY + cell / region.width]

  /**
   * Suitability in `[0,1]` for each resource type, as a function of world position.
   *
   * Each of these is a claim about geology or ecology, and each is checkable against a map: copper should
   * appear along volcanic arcs and nowhere else, coal in wet lowland basins, salt on the floor of terminal
   * lakes. That falsifiability is the reason to place resources causally in the first place - sprinkled
   * resources cannot be wrong, and cannot be discovered either.
   */
  fun suitabilityFor(type: ResourceType): (Vec2d) -> Double = { position ->
    val cell = cellAt(position)
    if (cell == null) {
      0.0
    } else {
      val above = elevation.data[cell] - seaLevel
      val submerged = above <= 0.0
      val rock = hardness.data[cell]
      val age = crustAge.data[cell]
      val arc = exp(-toConvergent.data[cell] / ARC_RANGE)

      when (type) {
        // Porphyry copper forms in the intrusive roots of arc volcanoes.
        ResourceType.COPPER ->
          if (submerged) 0.0 else arc * ramp(rock, 0.4, 0.8) * 0.9

        // Tin and tungsten sit in granite plutons: old, hard, cratonised crust.
        ResourceType.TIN ->
          if (submerged) 0.0 else ramp(rock, 0.6, 0.9) * ramp(age, 0.4, 0.85) * 0.8

        // Banded iron in ancient sedimentary shields: very old crust, low ground, middling hardness.
        ResourceType.IRON ->
          if (submerged) 0.0 else ramp(age, 0.55, 0.95) * ramp(600.0 - above, 0.0, 600.0) * 0.85

        // Gold with the arcs, but rarer and preferring the hardest host rock.
        ResourceType.GOLD_LODE ->
          if (submerged) 0.0 else arc * ramp(rock, 0.55, 0.9) * 0.7

        ResourceType.SILVER ->
          if (submerged) 0.0 else arc * ramp(rock, 0.45, 0.85) * 0.6

        // Placers are traced from the lodes, never placed directly.
        ResourceType.GOLD_PLACER -> 0.0

        // Coal is a buried swamp: soft sedimentary rock, low ground, and a wet climate to have grown in.
        ResourceType.COAL ->
          if (submerged) 0.0
          else ramp(1.0 - rock, 0.45, 0.85) *
              ramp(400.0 - above, 0.0, 400.0) *
              ramp(precipitation.data[cell], 700.0, 1800.0)

        // Salt from evaporation: the bed of a terminal lake, or a dry closed basin.
        ResourceType.SALT -> when {
          lakeAt(cell) < 0 -> 1.0
          submerged -> 0.0
          else -> ramp(600.0 - above, 0.0, 600.0) * ramp(900.0 - precipitation.data[cell], 0.0, 700.0)
        }

        // Quarryable stone wants hard rock actually exposed at the surface, so thin soil and some relief.
        ResourceType.STONE ->
          if (submerged) 0.0 else ramp(rock, 0.5, 0.85) * ramp(above, 100.0, 900.0)

        // Clay in floodplains: deposited sediment on flat wet ground.
        ResourceType.CLAY ->
          if (submerged) 0.0 else ramp(sediment.data[cell], 1.0, 8.0) * 0.9

        // Marble is metamorphosed limestone, so it needs both the sediments and the heat of a collision.
        ResourceType.MARBLE ->
          if (submerged) 0.0 else arc * ramp(age, 0.3, 0.8) * ramp(1.0 - rock, 0.2, 0.6) * 0.7

        ResourceType.TIMBER -> when (biomeAt(cell)) {
          Biome.TEMPERATE_FOREST, Biome.TEMPERATE_RAINFOREST, Biome.TAIGA -> 1.0
          Biome.TROPICAL_SEASONAL_FOREST, Biome.TROPICAL_RAINFOREST -> 0.8
          Biome.RIPARIAN -> 0.5
          else -> 0.0
        }

        ResourceType.FURS -> when (biomeAt(cell)) {
          Biome.TAIGA -> 1.0
          Biome.TUNDRA, Biome.TEMPERATE_FOREST -> 0.6
          Biome.ALPINE -> 0.4
          else -> 0.0
        }

        // Fish where there is water, best on a shallow shelf or in a lake rather than mid-ocean.
        ResourceType.FISH -> when {
          lakeAt(cell) != 0 -> 0.7
          !submerged -> 0.0
          else -> ramp(400.0 + above, 0.0, 400.0)
        }
      }.coerceIn(0.0, 1.0)
    }
  }

  /**
   * A smoothed field of nearby extractable value.
   *
   * Habitability wants "how much is worth having near here", and answering it by querying the deposit index
   * per cell would be a spatial query per cell. Stamping each deposit's contribution outwards instead is one
   * pass over the deposits, and it is the same answer.
   */
  fun valueField(deposits: List<PointMarker>, radius: Double): Grid {
    val grid = Grid(region.width, region.height)
    val typeChannel = DepositChannels.TYPE
    val radiusCells = (radius / metresPerCell).toInt() + 1

    for (deposit in deposits) {
      val type = ResourceType.entries[deposit.attribute(typeChannel).toInt()]
      val weight = type.value * deposit.attribute(DepositChannels.RICHNESS)
      if (weight <= 0.0) continue

      val centre = cellAt(deposit.position) ?: continue
      val cx = centre % region.width
      val cy = centre / region.width

      for (y in max(0, cy - radiusCells)..min(region.height - 1, cy + radiusCells)) {
        for (x in max(0, cx - radiusCells)..min(region.width - 1, cx + radiusCells)) {
          val dx = (x - cx) * metresPerCell
          val dy = (y - cy) * metresPerCell
          val distance = kotlin.math.sqrt(dx * dx + dy * dy)
          if (distance > radius) continue

          // Linear falloff rather than exponential: this is an accessibility measure, and a deposit forty
          // kilometres away is genuinely worth nothing to a village, not a little.
          grid.data[y * region.width + x] += weight * (1.0 - distance / radius)
        }
      }
    }

    for (i in grid.data.indices) {
      grid.data[i] = min(1.0, grid.data[i] / VALUE_SATURATION)
    }

    return grid
  }

  companion object {

    /** Distance over which arc-related mineralisation fades, in metres. */
    const val ARC_RANGE = 90_000.0

    /** Summed deposit weight that counts as "as good as it gets" for the value field. */
    const val VALUE_SATURATION = 2.5

    /** 0 below [low], 1 above [high], smooth between. */
    fun ramp(value: Double, low: Double, high: Double): Double {
      if (high <= low) return if (value >= high) 1.0 else 0.0
      return net.bestia.worldgen.vector.PolylineFeature.smoothstep((value - low) / (high - low))
    }

    fun read(ctx: GenContext, region: CellRegion): Terrain {
      val toConvergent = convergentDistance(ctx, region)

      return Terrain(
        region = region,
        metresPerCell = region.resolution.metresPerCell,
        seaLevel = ctx.config.seaLevel,
        elevation = Grid.from(ctx.layers.float(LayerId.ELEVATION)),
        hardness = Grid.from(ctx.layers.float(LayerId.ROCK_HARDNESS)),
        crustAge = Grid.from(ctx.layers.float(LayerId.CRUST_AGE)),
        sediment = Grid.from(ctx.layers.float(LayerId.SEDIMENT)),
        precipitation = Grid.resampled(ctx.layers.float(LayerId.PRECIPITATION), region),
        discharge = Grid.from(ctx.layers.float(LayerId.DISCHARGE)),
        waterLevel = Grid.from(ctx.layers.float(LayerId.WATER_LEVEL)),
        biome = ctx.layers.int(LayerId.BIOME),
        lakeId = ctx.layers.int(LayerId.LAKE_ID),
        flowDirection = ctx.layers.int(LayerId.FLOW_DIRECTION),
        toConvergent = toConvergent
      )
    }

    /**
     * Distance to the nearest convergent plate boundary, rasterised from the fault polylines.
     *
     * Read from the vector tier rather than re-derived from `plate_id`, which is the reason the tectonics
     * stage emits those polylines at all. Re-deriving would mean this stage picking its own tie-breaking for
     * where a boundary runs, and then two stages disagreeing about the location of the same fault.
     */
    private fun convergentDistance(ctx: GenContext, region: CellRegion): Grid {
      val metres = region.resolution.metresPerCell
      val onBoundary = BooleanArray(region.width * region.height)

      for (feature in ctx.features.query(region.toWorld())) {
        if (feature.kind != FeatureKind.FAULT) continue
        val fault = feature as? MarkerFeature ?: continue
        if (!isConvergent(fault)) continue

        // Walk the polyline at half-cell steps so no cell it crosses is missed.
        var s = 0.0
        while (s <= fault.centerline.length) {
          val point = fault.centerline.pointAt(s)
          val x = (point.x / metres).toInt() - region.minX
          val y = (point.y / metres).toInt() - region.minY
          if (x in 0 until region.width && y in 0 until region.height) {
            onBoundary[y * region.width + x] = true
          }
          s += metres * 0.5
        }
      }

      return DistanceTransform.euclideanMetres(region.width, region.height, metres) { x, y ->
        onBoundary[y * region.width + x]
      }.also { grid ->
        // No convergent boundary anywhere is a legitimate seed; cap so nothing downstream sees MAX_VALUE.
        val cap = max(region.width, region.height) * metres
        for (i in grid.data.indices) {
          if (grid.data[i] > cap) grid.data[i] = cap
        }
      }
    }

    private fun isConvergent(fault: MarkerFeature) =
      TectonicsStage.boundaryTypeOf(fault) == BoundaryType.CONVERGENT
  }
}

