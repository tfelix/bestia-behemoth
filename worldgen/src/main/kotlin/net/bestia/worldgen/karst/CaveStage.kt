package net.bestia.worldgen.karst

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.bio.BiomeStage
import net.bestia.worldgen.climate.ClimateStage
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FeatureIds
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.IntLayer
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
import net.bestia.worldgen.fields.PoissonDisk
import net.bestia.worldgen.geo.ErosionStage
import net.bestia.worldgen.geo.TectonicsStage
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import net.bestia.worldgen.voxel.RockColumn
import net.bestia.worldgen.voxel.StrataParams
import net.bestia.worldgen.voxel.Stratigraphy
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Tuning for [CaveStage]. */
data class CaveParams(

  /**
   * Target spacing between *candidate* systems, in metres.
   *
   * The finest possible spacing, not the resulting density: candidates are thinned by suitability and then
   * most of the survivors are thrown away again for want of a way in - see [CaveStage]. About one candidate in
   * fifteen becomes a cave.
   *
   * Used raw, so it is a real distance and the same on every world size - which is the property the table
   * below is checking, as much as the density itself. Share of dry land within four kilometres of a way in:
   *
   * | this | 128 km world | 512 km world |
   * | --- | --- | --- |
   * | 12 km | 3 systems, 1.8% | 33 systems, 1.2% |
   * | 7 km | 8 systems, 4.5% | 92 systems, 3.4% |
   * | 5 km | 15 systems, 8.1% | 178 systems, 6.4% |
   */
  val candidateSpacing: Double = 7_000.0,

  /**
   * Share of the rock column that must be soluble before a cave is even possible.
   *
   * **This is the density control, and it is deliberately the lithological one.** Widening
   * [candidateSpacing] would thin caves uniformly; raising this thins them *by rock*, so what survives is
   * clustered in the limestone-rich country where karst actually is - which is what makes a cave region a
   * thing a player can learn rather than a die roll. Bed facies are drawn at 27% limestone, so 0.45 asks for
   * a column with getting on for twice its share of it.
   *
   * Measured on the 128 km reference world at 30 km spacing, against the share of dry land within four
   * kilometres of a way in:
   *
   * | this | systems | land within 4 km |
   * | --- | --- | --- |
   * | 0.15 | 13 | 7.5% |
   * | 0.30 | 12 | 6.7% |
   * | 0.45 | 7 | 4.0% |
   *
   * Four percent is the judgement: a cave is a thing you go looking for or come across once in a long walk,
   * and not scenery. At 7.5% you meet one most times you cross a valley.
   */
  val minSolubleShare: Double = 0.45,

  /** Annual rainfall in mm at which dissolution is at its most effective. Below this, suitability ramps. */
  val wetEnough: Double = 900.0,

  /** How many galleries a system tries to grow. Not how many it keeps - a gallery can fail. */
  val galleriesPerSystem: Int = 4,

  /**
   * How many of those galleries look for daylight. The rest wander inward and end blind.
   *
   * One, and the one is the point. A cave system is a **resurgence with branches**: water enters through
   * fissures and leaves at one spring, so there is normally one way a person can walk in and a great deal of
   * passage that goes nowhere. Letting every gallery seek the surface gave four mouths per system, which reads
   * as a colander and, measured, put a way in within four kilometres of one land cell in nine - too common to
   * be worth finding.
   */
  val entranceGalleries: Int = 1,

  /** Metres between gallery stations. Short enough that a passage reads as a curve at one metre per voxel. */
  val stepLength: Double = 18.0,

  /** Longest a single gallery may run, in metres. */
  val maxGalleryLength: Double = 2_400.0,

  /** Largest bearing change per step, in radians. A passage wanders; it does not zigzag. */
  val maxTurn: Double = 0.30,

  /**
   * How strongly a gallery steers towards lower ground, 0 to 1.
   *
   * The mechanism that gives a system its entrance. Caves drain towards valleys, so a gallery that follows the
   * land down is doing what water did - and because terrain falls faster than a bedding plane does, the roof
   * thins as it goes until it reaches daylight. Without the bias a random walk almost never finds a hillside
   * and almost every system is rejected for having no way in.
   */
  val downhillBias: Double = 0.55,

  /** Fall of a passage floor per metre of run. Gentle: a cave stream is not a waterfall. */
  val gradient: Double = 0.02,

  /** Metres of rock a passage keeps over its roof, except at an entrance. */
  val minRoofCover: Double = 5.0,

  /** Metres below the surface a gallery starts. */
  val startDepth: Double = 45.0,

  /** Half-width and height of a passage, in metres, before the wall noise. */
  val minHalfWidth: Double = 1.6,
  val maxHalfWidth: Double = 4.2,
  val minHeight: Double = 2.2,
  val maxHeight: Double = 6.0
) : Params {

  init {
    require(candidateSpacing > 0.0) { "candidateSpacing must be positive, was $candidateSpacing" }
    require(minSolubleShare in 0.0..1.0) { "minSolubleShare must be a share, was $minSolubleShare" }
    require(wetEnough > 0.0) { "wetEnough must be positive, was $wetEnough" }
    require(galleriesPerSystem > 0) { "galleriesPerSystem must be positive, was $galleriesPerSystem" }
    require(entranceGalleries in 1..galleriesPerSystem) {
      "entranceGalleries must be between 1 and galleriesPerSystem $galleriesPerSystem, was $entranceGalleries"
    }
    require(stepLength > 0.0) { "stepLength must be positive, was $stepLength" }
    require(maxGalleryLength > stepLength) {
      "maxGalleryLength $maxGalleryLength must exceed one step of $stepLength"
    }
    require(maxTurn > 0.0 && maxTurn <= PI) { "maxTurn must be in (0, PI], was $maxTurn" }
    require(downhillBias in 0.0..1.0) { "downhillBias must be in [0,1], was $downhillBias" }
    require(gradient >= 0.0) { "gradient must not be negative, was $gradient" }
    require(minRoofCover > 0.0) { "minRoofCover must be positive, was $minRoofCover" }
    require(startDepth > minRoofCover) { "startDepth $startDepth must be below minRoofCover $minRoofCover" }
    require(minHalfWidth > 0.0) { "minHalfWidth must be positive, was $minHalfWidth" }
    require(minHalfWidth <= maxHalfWidth) { "minHalfWidth $minHalfWidth exceeds maxHalfWidth $maxHalfWidth" }
    require(minHeight > 0.0) { "minHeight must be positive, was $minHeight" }
    require(minHeight <= maxHeight) { "minHeight $minHeight exceeds maxHeight $maxHeight" }
  }

  fun overriddenBy(source: ParamsText.ParamsSource) = copy(
    candidateSpacing = source.double("candidateSpacing", candidateSpacing),
    minSolubleShare = source.double("minSolubleShare", minSolubleShare),
    wetEnough = source.double("wetEnough", wetEnough),
    galleriesPerSystem = source.int("galleriesPerSystem", galleriesPerSystem),
    entranceGalleries = source.int("entranceGalleries", entranceGalleries),
    stepLength = source.double("stepLength", stepLength),
    maxGalleryLength = source.double("maxGalleryLength", maxGalleryLength),
    maxTurn = source.double("maxTurn", maxTurn),
    downhillBias = source.double("downhillBias", downhillBias),
    gradient = source.double("gradient", gradient),
    minRoofCover = source.double("minRoofCover", minRoofCover),
    startDepth = source.double("startDepth", startDepth),
    minHalfWidth = source.double("minHalfWidth", minHalfWidth),
    maxHalfWidth = source.double("maxHalfWidth", maxHalfWidth),
    minHeight = source.double("minHeight", minHeight),
    maxHeight = source.double("maxHeight", maxHeight)
  )

  override fun digest() = ParamsDigest()
    .put("candidateSpacing", candidateSpacing)
    .put("minSolubleShare", minSolubleShare)
    .put("wetEnough", wetEnough)
    .put("galleriesPerSystem", galleriesPerSystem)
    .put("entranceGalleries", entranceGalleries)
    .put("stepLength", stepLength)
    .put("maxGalleryLength", maxGalleryLength)
    .put("maxTurn", maxTurn)
    .put("downhillBias", downhillBias)
    .put("gradient", gradient)
    .put("minRoofCover", minRoofCover)
    .put("startDepth", startDepth)
    .put("minHalfWidth", minHalfWidth)
    .put("maxHalfWidth", maxHalfWidth)
    .put("minHeight", minHeight)
    .put("maxHeight", maxHeight)
}

/** Station channels on a [FeatureKind.CAVE_PASSAGE]. */
object CaveChannels {

  /** Elevation of the passage floor at this station, in metres. */
  const val FLOOR = "floor"

  /** Metres from the floor to the roof. */
  const val HEIGHT = "height"

  /** Half-width of the passage at this station, in metres. */
  const val HALF_WIDTH = "half_width"

  // --- On the system and entrance markers, which are single-station point rows ----------------------

  /** Dense index of the system a passage or an entrance belongs to. The join, and never a `FeatureId`. */
  const val SYSTEM = "system"

  /** Passages in the system, and their total length in metres. */
  const val PASSAGES = "passages"
  const val LENGTH = "length"

  /** How far the deepest point of the system lies below the surface over it, in metres. */
  const val DEPTH = "depth"

  /** Half the width of the opening, in metres, for an entrance. */
  const val MOUTH = "mouth"

  /** Ground elevation at an entrance, so a caller need not resample the terrain to place something there. */
  const val ELEVATION = "elevation"
}

/**
 * Cave systems, placed on the rock that dissolves and the rain that dissolves it.
 *
 * ### Why this is a stage of its own
 *
 * A cave system is its own product with its own inputs - the stratigraphic column, rainfall, and the shape of
 * the land - and it is not an output of any stage that already exists. Bolting it onto `ResourceStage` because
 * both place sparse points would put the rock chemistry in a class about ore.
 *
 * ### The three decisions
 *
 * **Placement is a thinned Poisson process**, exactly as deposits are: sample candidate points at
 * [CaveParams.candidateSpacing] and accept each with probability equal to its suitability. Thinning a Poisson
 * process by a suitability yields a Poisson process with that suitability as its intensity, so no rejection
 * loop and no density normalisation is needed. Suitability is zero under water, under ice and on a glacier,
 * and otherwise **the soluble share of the rock column times a rainfall ramp** - so caves are lithology-driven
 * with no extra machinery, and the claim is falsifiable rather than decorative (`Invariants` checks it).
 *
 * **A gallery is a walk inside one bed.** It starts [CaveParams.startDepth] below the surface in a limestone
 * bed, and every step stays inside that bed's elevation band - which is what makes passages follow bedding
 * planes, as limestone caves do, instead of cutting across the strata like a mine adit. The band is recomputed
 * at each station because the structural datum warps, so a passage rides a fold rather than ignoring it.
 *
 * **A system with no way in is thrown away.** The walk steers downhill ([CaveParams.downhillBias]), so the
 * land falls away faster than the bedding plane does and the roof thins as the gallery goes. Where it would
 * thin past [CaveParams.minRoofCover] the gallery stops and that point becomes a [FeatureKind.CAVE_ENTRANCE].
 * **A candidate that grows no gallery reaching daylight is discarded entirely** - so every emitted system has
 * an entrance by construction, and the invariant asserting it can be checked on every seed instead of pinned
 * to a lucky one. A cave nobody can enter is `TODO.md`'s sixth habit with a nicer excuse.
 *
 * That last rule is also what makes caves *rare in the right places*: it is a filter on relief, so karst on a
 * hillside gets a mouth and karst under a plain does not. Roughly four in five suitable candidates are
 * rejected for it, which no density constant could have expressed.
 */
class CaveStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: CaveParams = CaveParams(),
  /**
   * The rock. Shared with the chunk tier through [Stratigraphy.of] rather than re-derived, so that "where is
   * the limestone" has one answer and a passage cannot be carved through granite because two files drifted.
   */
  private val strata: StrataParams = StrataParams()
) : Stage {

  override val id = ID
  override val version = 1

  override val paramsVersion get() = GenRng.hash(params.digest().value, strata.digest().value)

  override val dependencies = listOf(
    TectonicsStage.ID, ClimateStage.ID, ErosionStage.ID, HydrologyStage.ID, BiomeStage.ID
  )
  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Vector(FeatureKind.CAVE_SYSTEM),
    StageOutput.Vector(FeatureKind.CAVE_PASSAGE),
    StageOutput.Vector(FeatureKind.CAVE_ENTRANCE)
  )

  /** One accepted system, before it is turned into features. */
  private class System(
    val galleries: List<Gallery>,
    val entrances: List<Entrance>
  ) {
    val length get() = galleries.sumOf { it.line.length }
    val deepest get() = galleries.maxOf { it.maxCover }
    val centroid: Vec2d
      get() {
        var x = 0.0
        var y = 0.0
        var n = 0
        for (gallery in galleries) {
          for (point in gallery.line.points) {
            x += point.x
            y += point.y
            n++
          }
        }
        return Vec2d(x / n, y / n)
      }
  }

  private class Gallery(
    val line: Polyline,
    val floor: DoubleArray,
    val height: DoubleArray,
    val halfWidth: DoubleArray,
    val maxCover: Double
  )

  private class Entrance(val at: Vec2d, val ground: Double, val mouth: Double)

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val terrain = CaveTerrain(
      elevation = ctx.layers.float(LayerId.ELEVATION),
      precipitation = ctx.layers.float(LayerId.PRECIPITATION),
      waterLevel = ctx.layers.float(LayerId.WATER_LEVEL),
      biome = ctx.layers.int(LayerId.BIOME),
      strata = Stratigraphy.of(ctx.layers, ctx.config, strata),
      seaLevel = ctx.config.seaLevel,
      params = params
    )

    val nextId = FeatureIds.allocator(id)
    val rng = ctx.rng(CANDIDATE_STREAM)
    val features = ArrayList<VectorFeature>()
    var index = 0

    // The raw spacing, **not** `config.scaleByLength`. That helper shrinks a distance on a small world so the
    // world still gets a full complement of whatever is being placed, which is right for settlements and wrong
    // for caves: it holds the *count* constant, so a world sixteen times the area came out with seven cave
    // systems against the small world's eight - the same number of caves spread over sixteen times the ground,
    // which is a sixteenth of the density. Caves are a property of the rock, so they scale with area, exactly
    // as `ResourceStage` places its deposits.
    for (candidate in PoissonDisk.sample(region.toWorld(), params.candidateSpacing, rng)) {
      val score = terrain.suitabilityAt(candidate)
      if (score <= 0.0 || rng.nextDouble() > score) continue

      val system = grow(terrain, candidate, rng) ?: continue
      features.addAll(emit(system, index++, nextId))
    }

    return StageResult(features = features)
  }

  // --- Growing a system ------------------------------------------------------------------------------

  private fun grow(terrain: CaveTerrain, at: Vec2d, rng: GenRng): System? {
    val galleries = ArrayList<Gallery>()
    val entrances = ArrayList<Entrance>()

    // Galleries fan out from the same point, so a system reads as one network rather than as several caves
    // that happen to be near each other. The first bearing is random and the rest are spread around it.
    val firstBearing = rng.nextDouble() * 2.0 * PI

    for (g in 0 until params.galleriesPerSystem) {
      val bearing = firstBearing + g * (2.0 * PI / params.galleriesPerSystem)
      // Only the first few are resurgences. The rest get almost no downhill pull *and* are not allowed to
      // open even where they do come up under a hillside - they stop, choked, a few metres short of daylight.
      // Both halves are needed: the reduced pull alone still gave nearly four mouths per system, because in
      // hilly ground a passage that merely wanders passes under lower land soon enough.
      val seeks = g < params.entranceGalleries
      val bias = if (seeks) params.downhillBias else params.downhillBias * INTERIOR_PULL
      val walked = walk(terrain, at, bearing, bias, seeks, rng) ?: continue
      galleries.add(walked.first)
      walked.second?.let { entrances.add(it) }
    }

    // The rule that makes every emitted system enterable. Discarding the whole system rather than keeping the
    // blind galleries is deliberate: a network with no mouth is invisible to every player and every tool, so
    // it is indistinguishable from one that was never generated - and counting it would inflate every figure
    // this stage reports.
    if (galleries.isEmpty() || entrances.isEmpty()) return null

    return System(galleries, entrances)
  }

  /**
   * One gallery, and the entrance it found if it found one.
   *
   * @return null if the walk could not even start - no soluble bed at this point, or off the map
   */
  private fun walk(
    terrain: CaveTerrain,
    from: Vec2d,
    initialBearing: Double,
    downhillBias: Double,
    seeksDaylight: Boolean,
    rng: GenRng
  ): Pair<Gallery, Entrance?>? {
    val ground = terrain.groundAt(from)
    val start = terrain.solubleFloorNear(from, ground - params.startDepth)
    if (start.isNaN()) return null

    val vertices = ArrayList<Vec2d>()
    val floors = ArrayList<Double>()
    val heights = ArrayList<Double>()
    val halfWidths = ArrayList<Double>()

    var x = from.x
    var y = from.y
    var floor = start
    var bearing = initialBearing
    var maxCover = 0.0
    var entrance: Entrance? = null

    val steps = (params.maxGalleryLength / params.stepLength).toInt()

    for (step in 0..steps) {
      val here = Vec2d(x, y)
      if (!terrain.contains(here)) break

      val surface = terrain.groundAt(here)
      // A passage that has wandered out under a lake or the sea stops there. It is not an entrance - the carve
      // refuses to open a hole under standing water anyway - so continuing would only build a length of
      // gallery that no chunk will ever cut.
      if (terrain.isSubmerged(here, surface)) break

      val height = params.minHeight + rng.nextDouble() * (params.maxHeight - params.minHeight)
      val halfWidth = params.minHalfWidth + rng.nextDouble() * (params.maxHalfWidth - params.minHalfWidth)
      val cover = surface - (floor + height)

      // The roof has thinned to nothing. Either this is the way in - the gallery ends here and the station is
      // kept, so the passage actually reaches the mouth rather than stopping a step short of it - or the
      // passage is choked and simply ends, a few metres under a field that gives no sign of it.
      if (cover < params.minRoofCover) {
        if (seeksDaylight && vertices.size >= 2) {
          entrance = Entrance(here, surface, halfWidth)
          vertices.add(here); floors.add(floor); heights.add(height); halfWidths.add(halfWidth)
        }
        break
      }

      vertices.add(here); floors.add(floor); heights.add(height); halfWidths.add(halfWidth)
      maxCover = maxOf(maxCover, cover)

      // Wander, with a pull towards lower ground. `atan2` of the negative gradient is the downhill bearing;
      // blending bearings through their sine and cosine rather than through the angle keeps the turn short
      // the way round the circle it should be, which subtracting angles does not at the wrap.
      val turn = (rng.nextDouble() * 2.0 - 1.0) * params.maxTurn
      val downhill = terrain.downhillBearingAt(here)
      bearing = blend(bearing + turn, downhill, downhillBias)

      x += cos(bearing) * params.stepLength
      y += sin(bearing) * params.stepLength

      // Descend, then stay inside the bed. A gallery that has run out of soluble rock ends there.
      floor = terrain.keepInSolubleBed(Vec2d(x, y), floor - params.gradient * params.stepLength)
      if (floor.isNaN()) break
    }

    // `Polyline` drops repeated points and then demands two distinct ones, so a walk that turned back on
    // itself can come out shorter than it looks. runCatching rather than a length check, for the reason
    // `TownStage.wallStretches` records: the precondition is the authority on what it accepts.
    val line = runCatching { Polyline(vertices) }.getOrNull() ?: return null
    return Gallery(
      line = line,
      floor = DoubleArray(floors.size) { floors[it] },
      height = DoubleArray(heights.size) { heights[it] },
      halfWidth = DoubleArray(halfWidths.size) { halfWidths[it] },
      maxCover = maxCover
    ) to entrance
  }

  /** Blends two bearings by [weight], through the unit circle rather than through the angle. */
  private fun blend(from: Double, towards: Double, weight: Double): Double {
    val x = cos(from) * (1.0 - weight) + cos(towards) * weight
    val y = sin(from) * (1.0 - weight) + sin(towards) * weight
    return if (abs(x) < 1e-12 && abs(y) < 1e-12) from else atan2(y, x)
  }

  // --- Emitting --------------------------------------------------------------------------------------

  private fun emit(system: System, index: Int, nextId: () -> FeatureId): List<VectorFeature> {
    val out = ArrayList<VectorFeature>(system.galleries.size + system.entrances.size + 1)

    for (gallery in system.galleries) {
      out.add(
        MarkerFeature(
          id = nextId(),
          kind = FeatureKind.CAVE_PASSAGE,
          centerline = gallery.line,
          stations = StationTable.Builder(gallery.line.vertexCount)
            .channel(CaveChannels.FLOOR) { gallery.floor[it] }
            .channel(CaveChannels.HEIGHT) { gallery.height[it] }
            .channel(CaveChannels.HALF_WIDTH) { gallery.halfWidth[it] }
            .channel(CaveChannels.SYSTEM) { index.toDouble() }
            .build()
        )
      )
    }

    for (entrance in system.entrances) {
      out.add(
        PointMarker(
          id = nextId(),
          kind = FeatureKind.CAVE_ENTRANCE,
          position = entrance.at,
          attributes = StationTable.Builder(1)
            .channel(CaveChannels.SYSTEM) { index.toDouble() }
            .channel(CaveChannels.MOUTH) { entrance.mouth }
            .channel(CaveChannels.ELEVATION) { entrance.ground }
            .build()
        )
      )
    }

    out.add(
      PointMarker(
        id = nextId(),
        kind = FeatureKind.CAVE_SYSTEM,
        position = system.centroid,
        attributes = StationTable.Builder(1)
          .channel(CaveChannels.SYSTEM) { index.toDouble() }
          .channel(CaveChannels.PASSAGES) { system.galleries.size.toDouble() }
          .channel(CaveChannels.LENGTH) { system.length }
          .channel(CaveChannels.DEPTH) { system.deepest }
          .build()
      )
    )

    return out
  }

  /** The world as this stage reads it: rock, rain, water, and the shape of the land. */
  private class CaveTerrain(
    private val elevation: FloatLayer,
    private val precipitation: FloatLayer,
    private val waterLevel: FloatLayer,
    private val biome: IntLayer,
    private val strata: Stratigraphy,
    private val seaLevel: Double,
    private val params: CaveParams
  ) {

    private val bounds = elevation.region.toWorld()

    fun contains(at: Vec2d) = bounds.contains(at.x, at.y)

    /**
     * Ground height, **bilinear** - the same reading everything that decides land from water takes.
     *
     * Not bicubic, though the coarse elevation is smooth and bicubic would be the better shape for the
     * downhill bearing. The two interpolations disagree by metres near a coastline, and a cave placed against
     * one and checked against the other put a mouth two metres under the sea on one seed in forty. Agreeing
     * with the rest of the pipeline is worth more here than a smoother gradient.
     */
    fun groundAt(at: Vec2d): Double = elevation.sampleBilinear(at.x, at.y)

    fun isSubmerged(at: Vec2d, ground: Double): Boolean {
      if (ground <= seaLevel) return true
      val water = waterLevel.sampleBilinear(at.x, at.y)
      return !water.isNaN() && water > ground
    }

    /**
     * How likely a cave is here, in `[0,1]`.
     *
     * Zero where a cave cannot be, and otherwise the product of the two things that make karst: rock that
     * dissolves, and water to dissolve it with. Ice is excluded on top of that - a cave under an ice sheet is
     * a real landform and nothing in this pipeline models glacial hydrology, so claiming one would be
     * decoration rather than derivation.
     */
    fun suitabilityAt(at: Vec2d): Double {
      val ground = groundAt(at)
      if (isSubmerged(at, ground)) return 0.0

      val here = Biome.entries.getOrNull(biome.sampleNearest(at.x, at.y)) ?: return 0.0
      if (here == Biome.ICE_SHEET || here == Biome.GLACIER || here.isWater) return 0.0

      val soluble = solubleShareAt(at, ground)
      if (soluble < params.minSolubleShare) return 0.0

      val rain = (precipitation.sampleBilinear(at.x, at.y) / params.wetEnough).coerceIn(0.0, 1.0)
      return soluble * rain
    }

    /**
     * Share of the rock between the surface and the deepest a gallery could start that is soluble.
     *
     * Walked bed by bed rather than sampled at one depth, because a single sample lands in whichever bed the
     * noise happens to put there and turns a lithological question into a coin flip.
     */
    fun solubleShareAt(at: Vec2d, ground: Double): Double {
      val column = strata.columnAt(at.x, at.y)
      val bottom = ground - params.startDepth * SEARCH_DEPTHS

      var soluble = 0
      var total = 0
      var bed = column.bedIndexAt(ground)
      val lowest = column.bedIndexAt(bottom)

      while (bed >= lowest && total < MAX_BEDS) {
        // Below the basement there are no beds, only granite, and granite does not dissolve.
        if (column.topOfBed(bed) <= column.basementTop) break
        total++
        if (column.faciesOf(bed) in Stratigraphy.SOLUBLE) soluble++
        bed--
      }

      return if (total == 0) 0.0 else soluble.toDouble() / total
    }

    /**
     * A floor elevation inside a soluble bed near [want], or [Double.NaN] when there is none.
     *
     * Searches outwards from the target depth so a gallery starts in the *nearest* limestone rather than in
     * the first one found scanning down, which would bias every cave in the world to the deep end of its range.
     */
    fun solubleFloorNear(at: Vec2d, want: Double): Double {
      val column = strata.columnAt(at.x, at.y)
      val target = column.bedIndexAt(want)

      for (offset in 0 until MAX_BEDS) {
        for (bed in intArrayOf(target - offset, target + offset)) {
          val floor = floorInBed(column, bed)
          if (!standsInBed(column, bed, floor)) continue
          return floor
        }
      }
      return Double.NaN
    }

    /**
     * Keeps a floor inside a soluble bed as the passage moves, or [Double.NaN] where there is no longer one.
     *
     * The clamp is what makes this a bedding-plane passage rather than an adit: where the fold carries the
     * sequence up, the floor comes with it instead of boring through the roof of its own bed. Returning NaN
     * rather than giving up and continuing is what makes "a passage is in soluble rock" true by construction
     * - the walk ends at the edge of the limestone, which is where a cave ends.
     */
    fun keepInSolubleBed(at: Vec2d, floor: Double): Double {
      val column = strata.columnAt(at.x, at.y)
      val bed = column.bedIndexAt(floor)
      if (standsInBed(column, bed, floor)) return floor

      for (offset in 1..BED_STEP) {
        for (candidate in intArrayOf(bed - offset, bed + offset)) {
          val moved = floorInBed(column, candidate)
          if (standsInBed(column, candidate, moved)) return moved
        }
      }
      return Double.NaN
    }

    /**
     * Whether a passage standing on [floor], which falls in bed [bed], is really inside soluble rock.
     *
     * Three conditions, and each of the last two is a bug the invariant caught after the first looked
     * sufficient:
     *
     * - the bed is soluble;
     * - **the floor is above the basement**, tested against the elevation rather than against the bed's top
     *   face. The basement top follows rock hardness and rises through the sequence, so a bed can have its top
     *   in sediment and its bottom - where a passage floor sits - in granite. Thirty-seven seeds in forty had a
     *   gallery bored through the basement before this was stated properly;
     * - **the whole void fits under the bed's top**, not just the floor. The bed boundaries move as the walk
     *   goes - the structural datum warps and the thickness changes - so a floor that is comfortably inside its
     *   bed at one station can have the bed's roof come down through the passage at the next. Twenty-five seeds
     *   in forty had a passage whose upper half was in the shale above.
     *
     * The last one uses the tallest a passage may be rather than this station's height, so the answer does not
     * depend on a random draw that has not been made yet. Note the chunk tier's wall noise can still take a
     * roof a metre or two into the bed above, which is left alone: a cave roof breaking through a bedding plane
     * is a real thing, and the claim being made here is about where the passage *is*.
     */
    private fun standsInBed(column: RockColumn, bed: Int, floor: Double): Boolean =
      floor > column.basementTop &&
          floor + params.maxHeight <= column.topOfBed(bed) &&
          column.faciesOf(bed) in Stratigraphy.SOLUBLE

    /**
     * Where in a bed a passage's floor sits: a little way up from its base, never on the boundary.
     *
     * **Not on the base, and the difference is a bug that shipped in the first draft.** `bedIndexAt` of a bed's
     * own bottom face is that bed only if the division comes out exactly, and in floating point it lands one
     * bed lower about as often as not - so the facies check tested the rock *under* the passage while the void
     * was cut in the rock above it, and half the galleries came out in shale. Sitting a sixth of the way up
     * makes the index unambiguous, and it keeps the whole void inside the bed: the thinnest bed is 9 m
     * (`StrataParams.minBedThickness`) and the tallest passage 6 m, so 1.5 m of floor leaves it just inside.
     */
    private fun floorInBed(column: RockColumn, bed: Int): Double {
      val base = column.topOfBed(bed - 1)
      return base + (column.topOfBed(bed) - base) * FLOOR_IN_BED
    }

    /**
     * Bearing of the steepest descent of the coarse terrain, in radians.
     *
     * Sampled over a whole coarse cell rather than over a step, because a gallery step is 18 m and the
     * elevation raster is a kilometre - so a step-sized difference is interpolation noise with a slope in it,
     * and the walk would follow the bicubic's ripples instead of the valley.
     */
    fun downhillBearingAt(at: Vec2d): Double {
      val h = elevation.region.resolution.metresPerCell * 0.5
      val dx = groundAt(Vec2d(at.x + h, at.y)) - groundAt(Vec2d(at.x - h, at.y))
      val dy = groundAt(Vec2d(at.x, at.y + h)) - groundAt(Vec2d(at.x, at.y - h))
      return atan2(-dy, -dx)
    }

    private companion object {
      /** How many [CaveParams.startDepth]s down the soluble-share scan looks. */
      const val SEARCH_DEPTHS = 2.0

      /** Cap on the bed walk, so a thin-bedded column cannot make suitability the expensive part of the stage. */
      const val MAX_BEDS = 24

      /** How far a passage may step through the sequence when its bed runs out. */
      const val BED_STEP = 3

      /** How far up its bed a passage floor sits, as a share of the bed's thickness. See `floorInBed`. */
      const val FLOOR_IN_BED = 1.0 / 6.0
    }
  }

  companion object {
    val ID = StageId("caves")

    private const val CANDIDATE_STREAM = 0x0CA7E5L

    /**
     * Share of the downhill pull an interior gallery keeps.
     *
     * Not zero: a passage with no pull at all is a pure random walk, and a pure random walk in two dimensions
     * comes back on itself, so the interior galleries would coil round the entrance chamber instead of
     * reaching into the hill. A tenth is enough direction to make a passage go somewhere and not enough to
     * take it to a hillside.
     */
    const val INTERIOR_PULL = 0.10
  }
}
