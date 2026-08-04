package net.bestia.worldgen.geo

import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FeatureIds
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.ParamsText
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.Timings
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.mana.isStandableLand
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Ring
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import java.util.Arrays
import kotlin.math.max

/** Tuning for [VolcanismStage]. */
data class VolcanismParams(

  /**
   * How far an arc vent's heat reaches, in metres. Scaled by world length.
   *
   * Scaled, unlike the biome stage's `volcanicVentRange`, and the two are different questions. This one is a
   * *regional* term - "is this province volcanic country" - and a province is a fraction of a world rather than
   * a fixed number of kilometres, so it tracks the world the way `HistoryStage` scaled its own volcanism reach
   * before this stage existed. The vent range is the edifice itself, which is 10-30 km across on every world.
   */
  val arcRange: Double = 40_000.0,

  /** How far a hotspot cone's heat reaches, in metres. Scaled by world length, for [arcRange]'s reason. */
  val hotspotRange: Double = 60_000.0,

  /**
   * Convergence below which a boundary gets no arc vents at all.
   *
   * The main rarity knob for arc volcanism, and the one to reach for first if the volcanic share comes out too
   * high. A boundary barely past `BoundaryContact.TRANSFORM_THRESHOLD` is grinding sideways more than it is
   * subducting, and a chain of craters along it would be geology the terrain does not show.
   */
  val minConvergence: Double = 0.35,

  /** Spacing of vents along a convergent boundary, in metres. Scaled by world length. */
  val arcVentSpacing: Double = 55_000.0,

  /**
   * How far inland of the trench to look for the arc crest, in metres.
   *
   * `Orogeny` puts the crest 70 km from the boundary on the overriding side, so this is that number - but it is
   * used as a *search window* rather than as an offset, because the fault marker cannot say which side is
   * overriding. It carries `boundary_type`, `convergence` and `strength` and nothing else: no plate ids, no
   * ages, and `Polyline.project` returns an unsigned distance. So the vent goes at the highest bedrock within
   * this window perpendicular to the fault, which *is* the arc crest by construction - the orogeny stamped it
   * there - and needs no plate bookkeeping added to the marker to find it.
   */
  val arcVentOffset: Double = 70_000.0,

  /**
   * Youngest cones per hotspot chain that still have an open crater.
   *
   * Two of seven. This is where vent rarity comes from for free rather than by tuning: `HOTSPOT_DECAY` already
   * says the rest of a chain is lower and more eroded, which is to say extinct, so taking the young end is both
   * the geologically honest reading and a five-sevenths reduction nobody has to calibrate.
   */
  val activeChainLength: Int = 2
) : Params {

  init {
    require(arcRange > 0.0) { "arcRange must be positive, was $arcRange" }
    require(hotspotRange > 0.0) { "hotspotRange must be positive, was $hotspotRange" }
    require(minConvergence >= 0.0) { "minConvergence must not be negative, was $minConvergence" }
    require(arcVentSpacing > 0.0) { "arcVentSpacing must be positive, was $arcVentSpacing" }
    require(arcVentOffset >= 0.0) { "arcVentOffset must not be negative, was $arcVentOffset" }
    require(activeChainLength >= 0) { "activeChainLength must not be negative, was $activeChainLength" }
  }

  /**
   * Loadable from a params file, unlike most of the pipeline.
   *
   * The rarity knobs are exactly what a designer will want to sweep - "how much of the world is volcanic" is a
   * question you answer by trying numbers and looking at the biome mix, not by reasoning - so this is wired
   * rather than parked in `WorldParams.NOT_YET_LOADABLE`.
   */
  fun overriddenBy(source: ParamsText.ParamsSource) = copy(
    arcRange = source.double("arcRange", arcRange),
    hotspotRange = source.double("hotspotRange", hotspotRange),
    minConvergence = source.double("minConvergence", minConvergence),
    arcVentSpacing = source.double("arcVentSpacing", arcVentSpacing),
    arcVentOffset = source.double("arcVentOffset", arcVentOffset),
    activeChainLength = source.int("activeChainLength", activeChainLength)
  )

  override fun digest() = ParamsDigest()
    .put("arcRange", arcRange)
    .put("hotspotRange", hotspotRange)
    .put("minConvergence", minConvergence)
    .put("arcVentSpacing", arcVentSpacing)
    .put("arcVentOffset", arcVentOffset)
    .put("activeChainLength", activeChainLength)
}

/**
 * Where the world is volcanic, and where its craters are.
 *
 * Two products, because they answer two questions at two scales and a single one cannot do both.
 *
 * [LayerId.VOLCANISM] is the **regional** field: how exposed a province is to eruption, ashfall and ground
 * heat. It is what history rolls its eruptions against, what the resource stage places sulfur on, and what
 * `LocalTemperature` reads for its geothermal term.
 *
 * `FeatureKind.VOLCANIC_VENT` markers are the **discrete** answer: an actual crater, at an actual position. The
 * biomes are placed from distance to one of these rather than by thresholding the raster, and that distinction
 * is not a refinement - it is the difference between the feature working and not. Thresholding a field built
 * from distance-to-a-convergent-boundary selects a continuous band tens of kilometres either side of every
 * subduction zone in the world; on a 512 km world that is hundreds of kilometres of it, which is the opposite
 * of rare. A vent is a point, and a disc a few kilometres across around a point is an edifice.
 *
 * ### Why this is its own stage rather than more code in [TectonicsStage]
 *
 * Tectonics has every input this needs and it would have been the shorter change. But `Stage.version` reaches
 * `GenContext.rng`, so vent placement living in tectonics would mean every future retune of vent spacing
 * reseeding the plates and moving every mountain in the world. A separate stage confines that to vents, which
 * is exactly the failure `Stage.version`'s own KDoc warns about.
 *
 * ### Dependencies, and the one that looks gratuitous
 *
 * Tectonics for the faults and the hotspot cones. Glacial and hydrology for nothing but the **land mask** the
 * rank normalisation needs - see [LayerId.VOLCANISM] for why the output is a rank rather than a raw field.
 * `ManaStage` sets that precedent exactly: it depends on three stages solely to know where the land is.
 *
 * It cannot depend on `BiomeStage` for the same purpose, because the biomes depend on this - which is why
 * [isStandableLand] takes a nullable biome layer.
 */
class VolcanismStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: VolcanismParams = VolcanismParams()
) : Stage {

  override val id = ID

  override val version = 1

  override val paramsVersion get() = params.digest().value

  override val dependencies = listOf(TectonicsStage.ID, GlacialStage.ID, HydrologyStage.ID)

  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Raster(LayerId.VOLCANISM),
    StageOutput.Vector(FeatureKind.VOLCANIC_VENT),
    StageOutput.Vector(FeatureKind.LAVA_POOL)
  )

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val vents = Timings.measure("volcanism.vents") { placeVents(ctx, region) }
    val field = Timings.measure("volcanism.field") { volcanismField(ctx, region, vents) }

    rankAgainstLand(ctx, region, field)

    return StageResult(
      layers = listOf(field.toLayer(LayerId.VOLCANISM, region)),
      features = ventMarkers(vents) + lavaPools(ctx, vents)
    )
  }

  /**
   * A lava lake in the crater of the strongest vents.
   *
   * Not one per vent. A volcano with an open, standing lava lake is a rare thing even among active volcanoes -
   * Earth has a handful at a time - so this is gated on [LAVA_POOL_STRENGTH], and the rest of the vents are
   * craters with nothing molten showing. That gate is doing the same work [VolcanismParams.activeChainLength]
   * does for hotspots: making the dramatic thing rare by saying which cases earn it, rather than by rolling for
   * it.
   *
   * The surface elevation is stored on the feature rather than derived at chunk time, and that is the whole
   * reason this is an [AreaFeature]. `PondWaterSampler`'s KDoc makes the argument: a shoreline decided by an
   * exact integer `contains` test comes out identical in every chunk that touches it, and a stored elevation is
   * read back unchanged instead of being recomputed by two chunks that might not agree to the last bit.
   */
  private fun lavaPools(ctx: GenContext, vents: List<Vent>): List<VectorFeature> {
    val bedrock = ctx.layers.float(LayerId.BEDROCK_ELEVATION)
    val nextId = FeatureIds.blockAllocator(id, POOL_ID_BLOCK)

    return vents.asSequence()
      .filter { it.strength >= LAVA_POOL_STRENGTH }
      .map { vent ->
        val radius = ctx.config.scaleByLength(LAVA_POOL_RADIUS) * vent.strength
        val ring = Ring.warpedCircle(
          centre = vent.position,
          radius = radius,
          seed = GenRng.hash(ctx.config.seed, POOL_SALT, vent.position.x.toRawBits()),
          roughness = LAVA_POOL_ROUGHNESS
        )

        // Below the summit, not at it: the lake sits in the crater the cone was built around, so a pool level
        // with the peak would be a dome of lava standing on top of a mountain.
        val surface = bedrock.sampleBilinear(vent.position.x, vent.position.y) - LAVA_POOL_DEPRESSION

        AreaFeature(
          id = nextId(),
          kind = FeatureKind.LAVA_POOL,
          ring = ring,
          perimeter = StationTable.Builder(ring.vertexCount, periodic = true)
            .channel(CHANNEL_SURFACE_ELEVATION) { surface }
            .build()
        )
      }
      .toList()
  }

  /**
   * Every crater in the world: the arc vents along convergent boundaries, then the live end of each hotspot
   * chain.
   *
   * Both in one list and one id space, because downstream nothing cares which mechanism put a volcano
   * somewhere - `HistorySim` rolls per vent, the biomes measure distance to the nearest one. What the origin
   * channel is for is the resource stage, where it *does* matter: obsidian comes off a fresh basaltic flow and
   * that is a hotspot's product more than an arc's.
   */
  private fun placeVents(ctx: GenContext, region: CellRegion): List<Vent> {
    val vents = ArrayList<Vent>()
    vents.addAll(arcVents(ctx, region))
    vents.addAll(hotspotVents(ctx, region))

    // Indices are assigned here, over the concatenation, so they are dense from zero across both origins.
    // `HistorySim` keys a deterministic per-vent roll on this, so a gap or a duplicate would either waste a
    // stream or make two volcanoes erupt in lockstep for the life of the world.
    return vents
  }

  /**
   * Vents spaced along the convergent boundaries, each sitting on the arc crest.
   *
   * The crest is found rather than offset. See [VolcanismParams.arcVentOffset] for why - the fault marker does
   * not know which of its two sides is the overriding plate, and adding that to the marker would be a code
   * change to tectonics and therefore a reseed of the whole world.
   */
  private fun arcVents(ctx: GenContext, region: CellRegion): List<Vent> {
    // The layer rather than a `Grid` copy of it, because the crest search samples at arbitrary world positions
    // along a perpendicular and `sampleBilinear` lives on the layer.
    val bedrock = ctx.layers.float(LayerId.BEDROCK_ELEVATION)
    val metres = region.resolution.metresPerCell
    val seaLevel = ctx.config.seaLevel

    // Both scaled the way `Orogeny` scales the distance it stamps the crest at, or the window would look for a
    // crest at 70 km on a world that put one at 17.
    val offset = params.arcVentOffset / ctx.config.detailScale
    val spacing = ctx.config.scaleByLength(params.arcVentSpacing)

    val out = ArrayList<Vent>()

    // Sorted by feature id, so the vent order - and therefore every vent index - is a pure function of the seed
    // rather than of whatever order the spatial query happened to return.
    val faults = ctx.features.query(region.toWorld())
      .asSequence()
      .filter { it.kind == FeatureKind.FAULT }
      .filterIsInstance<MarkerFeature>()
      .sortedBy { it.id.value }

    for (fault in faults) {
      val convergence = constantChannel(fault, TectonicsStage.CHANNEL_CONVERGENCE) ?: continue
      if (convergence < params.minConvergence) continue
      val strength = constantChannel(fault, TectonicsStage.CHANNEL_STRENGTH) ?: convergence

      var along = spacing * 0.5
      while (along < fault.centerline.length) {
        val at = fault.centerline.pointAt(along)
        val ahead = fault.centerline.pointAt(min(along + metres, fault.centerline.length))
        val tangent = (ahead - at).normalized()

        val crest = highestPerpendicular(bedrock, region, metres, at, tangent.perpendicular(), offset)
        along += spacing

        // A submarine vent is a real thing and not one this models: nothing downstream of here - the biomes,
        // the resources, the ground heat - has anything to say about a crater under four kilometres of water.
        if (crest == null || bedrock.sampleBilinear(crest.x, crest.y) <= seaLevel) continue

        out.add(Vent(crest, strength.coerceIn(0.0, 1.0), VentOrigin.ARC))
      }
    }

    return out
  }

  /**
   * The highest bedrock within [offset] either side of [at], perpendicular to the fault.
   *
   * Either side, because which side the arc is on is exactly what the marker cannot say. Taking the maximum
   * over both is what makes that not matter: the overriding side carries a 2400 m crest and the subducting side
   * a trench, so the higher of the two is the arc every time and the trench never wins.
   */
  private fun highestPerpendicular(
    bedrock: FloatLayer,
    region: CellRegion,
    metres: Double,
    at: Vec2d,
    normal: Vec2d,
    offset: Double
  ): Vec2d? {
    val world = region.toWorld()
    var best: Vec2d? = null
    var bestHeight = -Double.MAX_VALUE

    val steps = max(1, (offset * PERPENDICULAR_OVERSHOOT / metres).toInt())
    for (step in -steps..steps) {
      val probe = at + normal * (step.toDouble() * metres)
      if (!world.contains(probe.x, probe.y)) continue

      val height = bedrock.sampleBilinear(probe.x, probe.y)
      if (height > bestHeight) {
        bestHeight = height
        best = probe
      }
    }

    return best
  }

  /**
   * The live end of every hotspot chain.
   *
   * `chain_index` 0 is the cone over the plume now; anything past [VolcanismParams.activeChainLength] is a
   * volcano that stopped erupting millions of years ago and is a mountain rather than a crater. Reading that
   * off the chain index is what makes hotspot vents rare without a rarity knob.
   */
  private fun hotspotVents(ctx: GenContext, region: CellRegion): List<Vent> =
    ctx.features.query(region.toWorld())
      .asSequence()
      .filter { it.kind == FeatureKind.HOTSPOT }
      .filterIsInstance<PointMarker>()
      .sortedBy { it.id.value }
      .mapNotNull { cone ->
        val index = pointChannel(cone, TectonicsStage.CHANNEL_CHAIN_INDEX) ?: return@mapNotNull null
        if (index.toInt() >= params.activeChainLength) return@mapNotNull null

        // A young cone is a tall cone - HOTSPOT_DECAY is an exponential in the index - so its own height
        // relative to the tallest a plume builds is a fair statement of how vigorous the vent is.
        val height = pointChannel(cone, TectonicsStage.CHANNEL_CONE_HEIGHT) ?: 0.0
        val strength = (height / HOTSPOT_REFERENCE_HEIGHT).coerceIn(0.0, 1.0)

        Vent(cone.position, strength, VentOrigin.HOTSPOT)
      }
      .toList()

  /**
   * The regional field: the worst heat any vent puts on a cell, tapered linearly with distance.
   *
   * A proximity maximum rather than a sum, so two vents forty kilometres apart do not add up to somewhere hotter
   * than either - which is what a sum does and what would make a dense arc read as a single enormous anomaly.
   */
  private fun volcanismField(ctx: GenContext, region: CellRegion, vents: List<Vent>): Grid {
    val field = Grid(region.width, region.height)
    if (vents.isEmpty()) return field

    val metres = region.resolution.metresPerCell
    val arcRange = ctx.config.scaleByLength(params.arcRange)
    val hotspotRange = ctx.config.scaleByLength(params.hotspotRange)

    for (y in 0 until region.height) {
      for (x in 0 until region.width) {
        val worldX = (region.minX + x + 0.5) * metres
        val worldY = (region.minY + y + 0.5) * metres

        var worst = 0.0
        for (vent in vents) {
          val range = if (vent.origin == VentOrigin.HOTSPOT) hotspotRange else arcRange
          val distance = vent.position.distanceTo(Vec2d(worldX, worldY))
          if (distance > range) continue
          worst = max(worst, vent.strength * (1.0 - distance / range))
        }

        field.data[y * region.width + x] = worst
      }
    }

    return field
  }

  /**
   * Replaces every positive value with its percentile rank among the **volcanic** land cells, and zeroes
   * everything else.
   *
   * `ManaStage.rankAgainstLand`'s construction with two deliberate differences, and the second of them is not a
   * refinement - it is what makes the layer usable at all.
   *
   * Water is zeroed rather than ranked on the land curve. Mana is what the rock holds and has an answer under
   * the sea; volcanism as every consumer here reads it is a statement about ground a player stands on, and a warm
   * number over open water would put ash and sulfur and ground heat where nobody can reach them.
   *
   * ### Cells with no volcanism at all are excluded from the ranking, not ranked at the bottom of it
   *
   * `ManaStage` can rank against all the land because `MANA_DENSITY` is a smooth noise field with a value
   * everywhere. This one is a **proximity maximum with a hard range cutoff**, so most of the land is exactly
   * zero - and a percentile rank over a distribution with a spike at zero puts *every one of those cells at the
   * same rank*, which is the height of the spike. Measured on three 128 km worlds before this was fixed: about
   * sixty per cent of the land was zero-volcanism, and every one of those cells came out at rank **0.598**.
   *
   * Two failures came out of that, and both are the kind that read as a tuning problem:
   *
   * - a threshold below the spike selects the whole world. `LocalTemperature`'s `geothermalFloor` of 0.55 warmed
   *   45% of the land on one seed and **99.7% on another**, because the spike landed either side of it - so the
   *   number that decides which provinces are volcanic meant something different on every world.
   * - the meaning of a given value was a function of how much of the world was volcanic. Fewer vents pushed the
   *   spike *up*, so the sparser a world's volcanism the warmer its ordinary country read.
   *
   * Ranking only the positive cells keeps everything the rank was wanted for - the hottest volcanic cell in any
   * world is 1.0, and a threshold is a percentile of volcanic country rather than of an absolute field nobody can
   * calibrate - while "not volcanic" stays exactly zero, which is the one value every consumer needs to be able
   * to trust.
   */
  private fun rankAgainstLand(ctx: GenContext, region: CellRegion, field: Grid) {
    val elevation = Grid.from(ctx.layers.float(LayerId.ELEVATION))
    val waterLevel = Grid.from(ctx.layers.float(LayerId.WATER_LEVEL))
    val seaLevel = ctx.config.seaLevel

    val volcanic = ArrayList<Double>(field.data.size / 8)
    val ranked = BooleanArray(field.data.size)
    for (i in field.data.indices) {
      ranked[i] = field.data[i] > 0.0 && isStandableLand(elevation, waterLevel, null, region, seaLevel, i)
      if (ranked[i]) volcanic.add(field.data[i])
    }

    // A world with no volcanism at all is a legitimate seed - no convergent boundary and no hotspot on land - and
    // a rank over an empty set has no answer. Everything is already zero, so there is nothing to do.
    if (volcanic.isEmpty()) {
      Arrays.fill(field.data, 0.0)
      return
    }

    volcanic.sort()
    val sorted = volcanic.toDoubleArray()
    val last = (sorted.size - 1).coerceAtLeast(1)

    for (i in field.data.indices) {
      field.data[i] = if (!ranked[i]) {
        0.0
      } else {
        (upperBound(sorted, field.data[i]).toDouble() / last).coerceAtMost(1.0)
      }
    }
  }

  /** Index of the first entry strictly greater than [value]; equivalently the count at or below it. */
  private fun upperBound(sorted: DoubleArray, value: Double): Int {
    var low = 0
    var high = sorted.size
    while (low < high) {
      val mid = (low + high) ushr 1
      if (sorted[mid] <= value) low = mid + 1 else high = mid
    }
    return low
  }

  private fun ventMarkers(vents: List<Vent>): List<VectorFeature> {
    val nextId = FeatureIds.blockAllocator(id, VENT_ID_BLOCK)

    return vents.mapIndexed { index, vent ->
      PointMarker(
        id = nextId(),
        kind = FeatureKind.VOLCANIC_VENT,
        position = vent.position,
        attributes = StationTable.Builder(1)
          .channel(CHANNEL_INDEX) { index.toDouble() }
          .channel(CHANNEL_STRENGTH) { vent.strength }
          .channel(CHANNEL_ORIGIN) { vent.origin.ordinal.toDouble() }
          .build()
      )
    }
  }

  /** One crater. Not a feature yet - [ventMarkers] turns it into one once the indices are dense. */
  private class Vent(val position: Vec2d, val strength: Double, val origin: VentOrigin)

  companion object {
    val ID = StageId("volcanism")

    /**
     * Station channels on a [FeatureKind.VOLCANIC_VENT] marker.
     *
     * [CHANNEL_INDEX] is dense from zero, like `SettlementChannels.INDEX`, and that is a contract rather than a
     * convenience: `HistorySim` keys its per-vent eruption roll on it, so history is only a pure function of the
     * seed while the indices are.
     */
    const val CHANNEL_INDEX = "index"
    const val CHANNEL_STRENGTH = "strength"
    const val CHANNEL_ORIGIN = "origin"

    /** Elevation of a [FeatureKind.LAVA_POOL]'s surface, in metres. The one channel a pool carries. */
    const val CHANNEL_SURFACE_ELEVATION = "surface_elevation"

    /** Vent strength at or above which a crater holds a standing lava lake rather than being merely a crater. */
    private const val LAVA_POOL_STRENGTH = 0.82

    /** Radius of a lava lake at full vent strength, in metres. Scaled by world length. */
    private const val LAVA_POOL_RADIUS = 110.0

    /** How far a crater lake's surface sits below the summit the cone was stamped to, in metres. */
    private const val LAVA_POOL_DEPRESSION = 30.0

    /** How far a pool's boundary wanders. Higher than a pond's, because nothing about a lava lake is round. */
    private const val LAVA_POOL_ROUGHNESS = 0.34

    private const val POOL_SALT = 0x4C617661506F6F6CL

    /**
     * Feature id blocks, one per emitter, for the reason `TectonicsStage` needs them: two plain allocators in
     * one stage both start at zero and mint duplicates that the feature store silently collapses.
     */
    private const val VENT_ID_BLOCK = 0
    private const val POOL_ID_BLOCK = 1

    /**
     * Tallest cone a plume builds, in metres, for scaling a hotspot vent's strength into 0..1.
     *
     * `TectonicsStage.OCEANIC_HOTSPOT_PEAK` times the top of its jitter range. Duplicated as a number rather
     * than read from there because it is a *normaliser* here, not the peak itself: were tectonics to raise its
     * peak, every hotspot vent in the world reading 1.0 would be the wrong correction, and re-tuning this is.
     */
    private const val HOTSPOT_REFERENCE_HEIGHT = 3_800.0 * 1.3

    /**
     * How far past [VolcanismParams.arcVentOffset] the crest search looks, as a multiple.
     *
     * The crest is a Gaussian 62 km wide centred on the offset, so a window at exactly the offset would find its
     * flank as often as its top wherever the boundary is not straight.
     */
    private const val PERPENDICULAR_OVERSHOOT = 1.5
  }
}

/** Which mechanism opened a vent. Ordinals reach a station channel, so append rather than reorder. */
enum class VentOrigin {
  /** Spaced along a convergent boundary, on the arc crest inland of the trench. */
  ARC,

  /** The live end of a hotspot chain, over a mantle plume. */
  HOTSPOT
}

/** Reads a channel that is constant along a fault, or null when the marker does not carry it. */
private fun constantChannel(fault: MarkerFeature, name: String): Double? {
  val stations = fault.stations ?: return null
  val channel = runCatching { stations.channel(name) }.getOrNull() ?: return null
  return stations.valueAt(channel, 0)
}

/** Reads a point marker's channel, or null when it does not carry it. */
private fun pointChannel(marker: PointMarker, name: String): Double? =
  runCatching { marker.attribute(name) }.getOrNull()

private fun min(a: Double, b: Double) = if (a < b) a else b
