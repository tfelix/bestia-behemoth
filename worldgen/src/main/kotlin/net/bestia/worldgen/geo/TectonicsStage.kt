package net.bestia.worldgen.geo

import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FeatureIds
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.fields.IntGrid
import net.bestia.worldgen.fields.Noise
import net.bestia.worldgen.fields.PoissonDisk
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Tuning for [TectonicsStage].
 *
 * A params object rather than constants in the stage, and rather than fields on `WorldConfig`. These
 * are the numbers a designer changes to make a world archipelagic or continental, and they belong in
 * data - loaded and validated at startup - not in code. Keeping them in one serialisable type now is
 * what makes that later change a deserialiser rather than a refactor.
 */
data class TectonicsParams(

  /**
   * Target plate spacing in metres. Null derives it from the world size, which is what any world
   * smaller than a real planet needs - 700 km plates in a 500 km world give you one plate.
   */
  val plateSpacing: Double? = null,

  /** Fraction of plates that are oceanic. Earth is about 0.6 by count. */
  val oceanicShare: Double = 0.6,

  /**
   * Fraction of the world that ends up above sea level.
   *
   * Enforced by shifting the whole heightfield so that the matching elevation quantile lands exactly
   * at sea level. Without it the land fraction swings from 5% to 80% between seeds depending on how
   * the Poisson sampler happened to place the continental plates, and most seeds are unusable.
   */
  val targetLandFraction: Double = 0.32,

  /**
   * How far below sea level the forced ocean margin is taken, in metres.
   *
   * Deep enough to be unmistakably open sea rather than shallows a player might mistake for a wadeable shelf,
   * and deep enough that erosion and hydrology treat it as the sink it is. See [OceanBorder].
   */
  val oceanBorderDepth: Double = 400.0,

  /** Wavelength of the ridged relief noise in metres - the spacing of individual ridge crests. */
  val reliefWavelength: Double = 38_000.0,

  /** Peak crest height in an inactive plate interior, in metres. */
  val interiorRelief: Double = 240.0,

  /**
   * Amplitude of the plate-independent regional swell, in metres.
   *
   * The knob that decides how much the coastline owes to the plate layout. At zero the shoreline is a
   * contour of the Voronoi diagram; turned up it wanders across plate boundaries and the tectonic
   * structure shows in the mountains rather than in the outline of the land.
   */
  val continentalSwell: Double = 540.0,

  /** Additional crest height at the heart of an orogen, in metres. */
  val orogenicRelief: Double = 1_750.0,

  /** Hotspot chain spacing in metres. Null derives it from the plate spacing. */
  val hotspotSpacing: Double? = null,

  /** Islands per hotspot chain. */
  val hotspotChainLength: Int = 7
) {
  init {
    require(oceanicShare in 0.0..1.0) { "oceanicShare must be in [0,1], was $oceanicShare" }
    require(targetLandFraction in 0.01..0.99) {
      "targetLandFraction must be in (0,1), was $targetLandFraction"
    }
    require(hotspotChainLength >= 1) { "hotspotChainLength must be at least 1" }
  }
}

/**
 * Stage 1: plate tectonics and the base heightfield.
 *
 * Starts from plates rather than from fractal noise, which is the difference between terrain that has
 * reasons and terrain that is the same everywhere. A range exists because two continental plates are
 * closing; a trench exists because one plate is going under another; a chain of islands exists because
 * a plate drifted over a hotspot. Every one of those is legible to a player who never reads a word of
 * documentation, and none of them survive being replaced by an octave of noise.
 *
 * Emits the plate boundaries as [FeatureKind.FAULT] markers so that the six stages which want them -
 * ore genesis, volcanism, resource placement - read one authoritative geometry instead of each
 * re-deriving it from `plate_id`.
 */
class TectonicsStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: TectonicsParams = TectonicsParams()
) : Stage {

  override val id = ID
  override val version = 1
  override val dependencies: List<StageId> = emptyList()
  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Raster(LayerId.BEDROCK_ELEVATION),
    StageOutput.Raster(LayerId.PLATE_ID),
    StageOutput.Raster(LayerId.ROCK_HARDNESS),
    StageOutput.Raster(LayerId.CRUST_AGE),
    StageOutput.Raster(LayerId.UPLIFT),
    StageOutput.Vector(FeatureKind.FAULT)
  )

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val metres = region.resolution.metresPerCell
    val bounds = region.toWorld()
    val spacing = params.plateSpacing ?: defaultSpacing(bounds.width, bounds.height)

    val plates = PlateSet.build(bounds, spacing, ctx.rng(PLATE_STREAM), params.oceanicShare)

    val elevation = Grid(region.width, region.height)
    val plateId = IntGrid(region.width, region.height)
    val uplift = Grid(region.width, region.height)
    val crustAge = Grid(region.width, region.height)

    /** How caught up in mountain building each cell is; drives relief roughness and rock hardness. */
    val intensity = Grid(region.width, region.height)

    /**
     * Continuous "how oceanic is this crust", 0 to 1.
     *
     * Rock hardness needs it, and taking the nearest plate's discrete type instead would put a step in
     * the hardness field along every plate boundary - which erosion would then turn into a straight
     * escarpment, reintroducing the polygon edges through the back door.
     */
    val oceanicity = Grid(region.width, region.height)

    val reliefSeed = GenRng.mix64(ctx.seed xor RELIEF_SALT)
    val undulationSeed = GenRng.mix64(ctx.seed xor UNDULATION_SALT)
    val continentSeed = GenRng.mix64(ctx.seed xor CONTINENT_SALT)

    val sample = DoubleArray(3)
    val scratch = DoubleArray(4)

    for (y in 0 until region.height) {
      val worldY = (region.minY + y + 0.5) * metres
      for (x in 0 until region.width) {
        val worldX = (region.minX + x + 0.5) * metres
        val i = elevation.index(x, y)

        plates.sampleInto(worldX, worldY, sample, scratch)
        val own = plates.plates[sample[0].toInt()]
        val otherId = sample[1].toInt()
        val distance = sample[2]

        plateId.data[i] = own.id

        // Cross-fade weight: 0 exactly on the boundary, 1 once far enough inside the plate.
        //
        // Every quantity below is asymmetric - it depends on which plate the cell belongs to - and so
        // every one of them steps discontinuously at the boundary if taken from the nearest plate alone.
        // Continents then come out visibly polygonal, with a straight cliff along every plate edge, which
        // is the single most recognisable failure of a Voronoi-based generator and the reason the
        // architecture document warns about it twice. Evaluating each quantity from *both* plates'
        // points of view and cross-fading on boundary distance makes all of them continuous: at the
        // boundary both sides compute the same average, because the average does not care which plate is
        // called "own".
        val fade = PolylineFeature.smoothstep(distance / (spacing * BOUNDARY_BLEND))

        var z: Double
        var age: Double
        var caught = 0.0
        var rise: Double
        var oceanic: Double

        if (otherId >= 0) {
          val other = plates.plates[otherId]
          val contact = plates.contact(own.id, other.id)

          z = blend(
            own.baseElevation + Orogeny.elevationAt(contact, own, other, distance),
            other.baseElevation + Orogeny.elevationAt(contact, other, own, distance),
            fade
          )
          rise = blend(
            Orogeny.upliftAt(contact, own, other, distance),
            Orogeny.upliftAt(contact, other, own, distance),
            fade
          )
          age = blend(own.age, other.age, fade)
          oceanic = blend(own.oceanicity, other.oceanicity, fade)
          caught = Orogeny.orogenicIntensity(contact, distance)

          // Oceanic crust is created at a ridge and ages as it spreads away from it, which is why the
          // sea floor gets deeper and older with distance from the mid-ocean ridge.
          if (contact.type == BoundaryType.DIVERGENT && (own.isOceanic || other.isOceanic)) {
            age *= 1.0 - exp(-distance / RIDGE_AGE_SCALE)
          }
        } else {
          z = own.baseElevation
          age = own.age
          oceanic = own.oceanicity
          rise = if (own.isOceanic) 0.0 else Orogeny.INTERIOR_UPLIFT
        }

        crustAge.data[i] = age
        oceanicity.data[i] = oceanic

        // Ridged noise for crests, plain fbm for the broad undulation underneath it. Both are scaled
        // by how old the plate is: an ancient craton has had a billion years of erosion and is smooth,
        // young crust is rough. Using the blended age keeps the roughness continuous too.
        val roughness = 1.0 - AGE_SMOOTHING * age
        val crest = Noise.ridged(
          reliefSeed, worldX / params.reliefWavelength, worldY / params.reliefWavelength, RELIEF_OCTAVES
        )
        val undulation = Noise.fbm(
          undulationSeed,
          worldX / (params.reliefWavelength * UNDULATION_FACTOR),
          worldY / (params.reliefWavelength * UNDULATION_FACTOR),
          UNDULATION_OCTAVES
        )

        // The ridged field averages around RIDGED_MEAN rather than zero, so subtract it: otherwise
        // every relief amplitude change would also silently move the whole continent up or down.
        z += (crest - RIDGED_MEAN) * (params.interiorRelief + params.orogenicRelief * caught) * roughness
        z += undulation * params.interiorRelief * roughness

        // Swell at a wavelength longer than a plate, and completely independent of where the plates are.
        //
        // This is what stops coastlines being plate-shaped. Every other term in this stage is derived
        // from the plate layout, so without it the shoreline is a contour of the plate diagram and reads
        // as a polygon however much the boundaries themselves are warped - the eye finds the Voronoi cell
        // from the coastline, not from the boundary. Amplitude has to be comparable to the difference
        // between a continental interior and the shelf around it, or it will not move a shoreline at all.
        z += Noise.fbm(
          continentSeed,
          worldX / (spacing * CONTINENT_WAVELENGTH),
          worldY / (spacing * CONTINENT_WAVELENGTH),
          CONTINENT_OCTAVES
        ) * params.continentalSwell

        elevation.data[i] = z
        uplift.data[i] = rise
        intensity.data[i] = caught
      }
    }

    addHotspotChains(ctx, region, bounds, plates, spacing, elevation)

    // The ocean margin is decided here, before the land fraction is normalised against the interior and well
    // before anything downstream runs. That ordering is the whole point of putting it in this stage: erosion,
    // hydrology, biomes and settlement all see nothing but deep water at the world edge, so no river tries to
    // drain across the seam and no town gets founded on ground a player would walk off.
    val border = OceanBorder.of(ctx.config, params.oceanBorderDepth, region, metres, region.width)
    normaliseLandFraction(elevation, ctx.config.seaLevel, border::isInteriorCell)
    border.applyTo(elevation, ctx.config.seaLevel)

    val hardness = rockHardness(ctx, region, elevation, crustAge, oceanicity, intensity)
    val faults = traceFaults(plateId, region, plates)

    return StageResult(
      layers = listOf(
        elevation.toLayer(LayerId.BEDROCK_ELEVATION, region),
        plateId.toLayer(LayerId.PLATE_ID, region),
        hardness.toLayer(LayerId.ROCK_HARDNESS, region),
        crustAge.toLayer(LayerId.CRUST_AGE, region),
        uplift.toLayer(LayerId.UPLIFT, region)
      ),
      features = faults
    )
  }

  /**
   * Volcanic chains: a hotspot is fixed in the mantle while the plate above it moves, so it stamps a
   * line of progressively older, more eroded cones onto the drifting plate.
   *
   * Applied to the raster rather than emitted as vector features because a volcanic island is 30-80 km
   * across - comfortably wider than the three coarse cells that are the threshold for pushing a
   * feature into the vector tier.
   */
  private fun addHotspotChains(
    ctx: GenContext,
    region: CellRegion,
    bounds: Aabb,
    plates: PlateSet,
    spacing: Double,
    elevation: Grid
  ) {
    val metres = region.resolution.metresPerCell
    val rng = ctx.rng(HOTSPOT_STREAM)
    val origins = PoissonDisk.sample(bounds, params.hotspotSpacing ?: spacing * HOTSPOT_SPACING_FACTOR, rng)

    val sample = DoubleArray(3)
    val scratch = DoubleArray(4)

    for (origin in origins) {
      plates.sampleInto(origin.x, origin.y, sample, scratch)
      val plate = plates.plates[sample[0].toInt()]

      // The chain trails *behind* the plate's motion: the island over the hotspot now is the youngest.
      val direction = plate.drift.normalized()
      if (direction.lengthSquared == 0.0) continue

      val step = spacing * HOTSPOT_STEP_FACTOR
      val peak = if (plate.isOceanic) OCEANIC_HOTSPOT_PEAK else CONTINENTAL_HOTSPOT_PEAK
      val sideways = direction.perpendicular()

      var along = 0.0
      for (k in 0 until params.hotspotChainLength) {
        // Jitter each island's own position along and across the track, not just the chain's spacing.
        // A single spacing per chain produces a line of perfectly evenly spaced dots, and evenly spaced
        // is the one thing a volcanic chain never is - the regularity reads as a rendering artefact
        // rather than as islands.
        along += step * (0.75 + rng.nextDouble() * 0.5)
        val drift = sideways * (step * (rng.nextDouble() - 0.5) * HOTSPOT_WANDER)
        val centre = origin - direction * along + drift
        if (!bounds.contains(centre.x, centre.y)) continue

        val height = peak * exp(-k * HOTSPOT_DECAY) * (0.7 + rng.nextDouble() * 0.6)
        val radius = spacing * HOTSPOT_RADIUS_FACTOR * (0.75 + rng.nextDouble() * 0.5)

        stampCone(elevation, region, metres, centre, radius, height)
      }
    }
  }

  private fun stampCone(
    elevation: Grid,
    region: CellRegion,
    metres: Double,
    centre: Vec2d,
    radius: Double,
    height: Double
  ) {
    val minCellX = max(0, ((centre.x - radius) / metres).toInt() - region.minX - 1)
    val maxCellX = min(region.width - 1, ((centre.x + radius) / metres).toInt() - region.minX + 1)
    val minCellY = max(0, ((centre.y - radius) / metres).toInt() - region.minY - 1)
    val maxCellY = min(region.height - 1, ((centre.y + radius) / metres).toInt() - region.minY + 1)

    for (y in minCellY..maxCellY) {
      val worldY = (region.minY + y + 0.5) * metres
      for (x in minCellX..maxCellX) {
        val worldX = (region.minX + x + 0.5) * metres
        val t = Vec2d(worldX, worldY).distanceTo(centre) / radius
        if (t >= 1.0) continue

        // A pointed cone rather than a smoothstep dome: volcanoes have summits. The exponent above 1
        // still gives zero slope at the toe, so it blends into the sea floor without a rim.
        elevation.data[elevation.index(x, y)] += height * (1.0 - t).pow(CONE_SHARPNESS)
      }
    }
  }

  /**
   * Shifts the whole heightfield so that exactly [params].targetLandFraction of it is above sea level.
   *
   * Via a histogram rather than a sort: sorting 16 million doubles to find one quantile costs more
   * than the rest of this stage put together, and a bin width of a few metres is far finer than the
   * question being asked.
   */
  private fun normaliseLandFraction(elevation: Grid, seaLevel: Double, interior: (Int) -> Boolean) {
    val low = elevation.min()
    val high = elevation.max()
    if (high - low < 1e-9) return

    val bins = IntArray(QUANTILE_BINS)
    val scale = QUANTILE_BINS / (high - low)
    // Interior cells only. Counting the forced ocean margin would have the quantile see a world that is mostly
    // sea and raise everything to compensate, which lifts the margin back above the waterline - the normaliser
    // undoing the very thing the margin is for.
    var counted = 0L
    for (i in elevation.data.indices) {
      if (!interior(i)) continue
      bins[((elevation.data[i] - low) * scale).toInt().coerceIn(0, QUANTILE_BINS - 1)]++
      counted++
    }
    if (counted == 0L) return

    val targetBelow = ((1.0 - params.targetLandFraction) * counted).toLong()
    var cumulative = 0L
    var bin = 0
    while (bin < QUANTILE_BINS - 1 && cumulative + bins[bin] < targetBelow) {
      cumulative += bins[bin]
      bin++
    }

    // Linear position within the bin the quantile falls in, so the result does not snap to bin edges.
    val within = if (bins[bin] > 0) (targetBelow - cumulative).toDouble() / bins[bin] else 0.0
    val quantile = low + (bin + within) / scale

    val shift = seaLevel - quantile
    for (i in elevation.data.indices) {
      elevation.data[i] += shift
    }
  }

  /**
   * Rock hardness, which is what makes erosion produce interesting terrain instead of uniform mush.
   *
   * Runs as a second pass because it reads the *normalised* elevation: whether a cell is a
   * sedimentary basin is a question about how low it is relative to sea level, and sea level only
   * means anything after the land fraction has been fixed.
   */
  private fun rockHardness(
    ctx: GenContext,
    region: CellRegion,
    elevation: Grid,
    crustAge: Grid,
    oceanicity: Grid,
    intensity: Grid
  ): Grid {
    val metres = region.resolution.metresPerCell
    val seed = GenRng.mix64(ctx.seed xor HARDNESS_SALT)
    val seaLevel = ctx.config.seaLevel
    val hardness = Grid(region.width, region.height)

    for (y in 0 until region.height) {
      val worldY = (region.minY + y + 0.5) * metres
      for (x in 0 until region.width) {
        val i = hardness.index(x, y)
        val above = elevation.data[i] - seaLevel
        val caught = intensity.data[i]
        val oceanic = oceanicity.data[i]

        // Basalt is tougher than the average continental cover but softer than a craton's granite.
        var h = 0.44 + 0.14 * oceanic

        // Cratonisation: old continental crust is granite and gneiss, and it does not yield.
        h += crustAge.data[i] * 0.30

        // Orogens expose intrusives and metamorphics.
        h += caught * 0.16

        // Sedimentary basins: low, quiet continental ground, filled with soft mudstone and sandstone.
        // This is where erosion carves badlands and wide floodplains rather than gorges.
        //
        // Every factor is a smooth ramp rather than a threshold. A threshold on elevation would put a
        // hardness cliff along a contour line, and erosion would then turn that contour into a visible
        // escarpment - a landform created by a comparison operator rather than by geology.
        val basin = (1.0 - oceanic) *
            PolylineFeature.smoothstep((BASIN_ELEVATION - above) / BASIN_ELEVATION) *
            PolylineFeature.smoothstep((BASIN_QUIET - caught) / BASIN_QUIET)
        h -= 0.26 * basin

        h += Noise.fbm(
          seed, (region.minX + x + 0.5) * metres / HARDNESS_WAVELENGTH, worldY / HARDNESS_WAVELENGTH, 3
        ) * 0.14

        hardness.data[i] = h.coerceIn(0.05, 0.98)
      }
    }

    return hardness
  }

  /**
   * Cross-fade between a quantity computed from this side of a boundary and the same quantity computed
   * from the other side.
   *
   * At `fade == 0` - on the boundary - both plates produce the same value, because the mean of the two
   * does not depend on which one is called "own". That symmetry is what makes the field continuous.
   */
  private fun blend(own: Double, other: Double, fade: Double): Double {
    val w = 0.5 + 0.5 * fade
    return own * w + other * (1.0 - w)
  }

  private fun traceFaults(
    plateId: IntGrid,
    region: CellRegion,
    plates: PlateSet
  ): List<MarkerFeature> {
    val nextId = FeatureIds.allocator(id)

    return BoundaryTracer.trace(plateId, region).map { trace ->
      val contact = plates.contact(trace.plateA, trace.plateB)
      MarkerFeature(
        id = nextId(),
        kind = FeatureKind.FAULT,
        centerline = trace.line,
        stations = faultStations(trace.line, contact)
      )
    }
  }

  /**
   * Boundary attributes as station channels.
   *
   * All three are constant along the line, because the classification is a property of the plate
   * *pair* rather than of a position on the boundary. They are stations anyway rather than fields on a
   * bespoke type, so that a later stage which does vary something along a fault - vent spacing, say -
   * adds a channel instead of a class. Interpolating a constant channel is exact, so nothing is lost.
   */
  private fun faultStations(line: Polyline, contact: BoundaryContact): StationTable =
    StationTable.Builder(line.vertexCount)
      .channel(CHANNEL_BOUNDARY_TYPE) { contact.type.ordinal.toDouble() }
      .channel(CHANNEL_CONVERGENCE) { contact.convergence }
      .channel(CHANNEL_STRENGTH) { contact.strength }
      .build()

  /** Plate spacing for a world too small for real plates: about five plates across the short edge. */
  private fun defaultSpacing(width: Double, height: Double): Double =
    (min(width, height) / 5.0).coerceIn(MIN_PLATE_SPACING, MAX_PLATE_SPACING)

  companion object {
    val ID = StageId("tectonics")

    /** Station channels on a [FeatureKind.FAULT] marker. */
    const val CHANNEL_BOUNDARY_TYPE = "boundary_type"
    const val CHANNEL_CONVERGENCE = "convergence"
    const val CHANNEL_STRENGTH = "strength"

    private const val PLATE_STREAM = 1L
    private const val HOTSPOT_STREAM = 2L

    private const val RELIEF_SALT = 0x51D4B2A7C3E19F0L
    private const val UNDULATION_SALT = 0x2C7E9A1F5B3D840L
    private const val HARDNESS_SALT = 0x6B1F3C8E27A594DL
    private const val CONTINENT_SALT = 0x74E82B15C6A39D0L

    private const val RELIEF_OCTAVES = 6
    private const val UNDULATION_OCTAVES = 4
    private const val CONTINENT_OCTAVES = 3

    /** Wavelength of the regional swell, as a multiple of plate spacing. Longer than a plate. */
    private const val CONTINENT_WAVELENGTH = 1.5

    /** Wavelength of the broad undulation, as a multiple of the crest wavelength. */
    private const val UNDULATION_FACTOR = 5.0

    /** Mean of [Noise.ridged] at the default gain; subtracted so relief has no net offset. */
    private const val RIDGED_MEAN = 0.33

    /** How much of the relief an ancient craton has lost. */
    private const val AGE_SMOOTHING = 0.62

    /**
     * Width of the cross-fade at a plate boundary, as a fraction of plate spacing.
     *
     * Generous on purpose. A real continental margin grades into the ocean floor over a hundred
     * kilometres or more, so blending over roughly a third of the plate spacing is both what removes
     * the polygon edges and what produces a continental shelf rather than a wall.
     */
    private const val BOUNDARY_BLEND = 0.45

    /** Distance over which oceanic crust ages away from a spreading ridge, in metres. */
    private const val RIDGE_AGE_SCALE = 300_000.0

    private const val MIN_PLATE_SPACING = 50_000.0
    private const val MAX_PLATE_SPACING = 700_000.0

    private const val HOTSPOT_SPACING_FACTOR = 2.2
    private const val HOTSPOT_STEP_FACTOR = 0.11
    private const val HOTSPOT_RADIUS_FACTOR = 0.075
    private const val HOTSPOT_DECAY = 0.4

    /** How far an island may sit off the hotspot track, as a fraction of the chain step. */
    private const val HOTSPOT_WANDER = 0.8
    private const val OCEANIC_HOTSPOT_PEAK = 3_800.0
    private const val CONTINENTAL_HOTSPOT_PEAK = 1_500.0
    private const val CONE_SHARPNESS = 1.6

    private const val BASIN_ELEVATION = 280.0
    private const val BASIN_QUIET = 0.15
    private const val HARDNESS_WAVELENGTH = 26_000.0

    private const val QUANTILE_BINS = 4096
  }
}
