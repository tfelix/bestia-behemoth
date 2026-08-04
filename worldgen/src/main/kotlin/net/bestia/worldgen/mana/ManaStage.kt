package net.bestia.worldgen.mana

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.bio.BiomeStage
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.ParamsText
import net.bestia.worldgen.core.Parallel
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.fields.DistanceTransform
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.fields.Noise
import net.bestia.worldgen.geo.GlacialStage
import net.bestia.worldgen.geo.TectonicsStage
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.MarkerFeature
import kotlin.math.exp
import kotlin.math.max

/** Tuning for [ManaStage]. */
data class ManaParams(

  /**
   * Wavelength of the broad mana field, in metres, before the warp.
   *
   * **Used raw, not through `scaleByLength`, and that is the deliberate half of this number.** Detail scale
   * exists to give a small world the feature *density* of a large one, which is right for rivers and ore and
   * wrong here: a mana province is a region a player walks across, and how long that takes is a fact about
   * the player rather than about the world's size. Scaled, the 128 km world got 8 km provinces - a twenty
   * minute walk - while the 512 km world got 32 km ones, for the same "ten provinces" on both. `CaveStage`
   * makes the same choice for the same reason.
   *
   * At 40 km the 128 km world sees 3.2 cycles across an axis and the 512 km world 12.8, so provinces come out
   * 20 to 40 km across on both and a big world simply has more of them.
   *
   * **Count the provinces before believing this number.** The field is warped and stretched, so what comes
   * out is not `worldWidth / wavelength`, and the first value tried here - 12 km after scaling - produced
   * thirty-odd patches on the genesis world when the intent was around ten.
   */
  val wavelength: Double = 40_000.0,

  /**
   * Octaves of the broad field.
   *
   * Three rather than four. The [broadLow]/[broadHigh] stretch multiplies the field's contrast by about
   * three, and it does that to the fine octaves as well as the coarse ones - so the fourth octave, which was
   * a texture on the raw field, came out as separate islands the size of a chunk once the field was
   * stretched. Adding an octave here is not free the way it is on a field consumed raw.
   */
  val octaves: Int = 3,

  /**
   * Wavelength of the domain warp, in metres. Raw, like [wavelength].
   *
   * About twice [wavelength], the ratio `voxel/SurfaceSampler.kt` uses between its jitter and patch fields,
   * so the warp bends whole provinces rather than mottling their interiors.
   */
  val warpWavelength: Double = 80_000.0,

  /**
   * How far the warp displaces a sample, in metres. Raw, like [wavelength].
   *
   * About 0.3 of [wavelength]: enough to take the characteristic lumpiness off fbm, below the half-wavelength
   * at which a warp starts folding the field onto itself and producing pinched provinces.
   */
  val warpAmplitude: Double = 12_000.0,

  /**
   * The broad field's value that maps to zero mana, and the one that maps to one.
   *
   * **Measured, not chosen.** Four-octave fbm mapped to `[0,1]` is not remotely uniform - on the 128 km world
   * it comes out `p05 = 0.348, p50 = 0.500, p95 = 0.652`, so nine tenths of the world sits inside a third of
   * the nominal range. Used raw it produced a field whose whole dynamic range was 0.25 to 0.85, which made
   * every downstream threshold meaningless: a "high mana" cut at 0.75 was unreachable and one at 0.55 caught
   * a third of the world.
   *
   * A smoothstep between these two spreads the bulk of the distribution across the full range. Measured over
   * the same world, share of cells above 0.75 and below 0.25:
   *
   * | cuts | above 0.75 | below 0.25 |
   * | --- | --- | --- |
   * | 0.30 - 0.62 | 43.7% | 16.4% |
   * | 0.35 - 0.65 | 28.9% | 29.6% |
   * | 0.38 - 0.70 | 15.1% | 43.3% |
   *
   * 0.35 to 0.65 is the balanced one, and balance is what a field several unrelated consumers threshold on
   * wants - a skew here becomes a skew in the corrupted share, the storm rate and the spawn levels at once.
   */
  val broadLow: Double = 0.35,

  /** See [broadLow]. */
  val broadHigh: Double = 0.65,

  /**
   * How far a convergent plate boundary's influence reaches, in metres. Raw, like [wavelength].
   *
   * Between `ResourceStage`'s arc range and the orogeny falloff, so mana wells up where the crust is being
   * worked rather than in a pattern unrelated to anything else on the map.
   */
  val faultRange: Double = 35_000.0,

  /**
   * How much a convergent boundary adds, right on top of it.
   *
   * **0.30 was the first attempt and it made the mana map a picture of the fault network** - the exported
   * PNG was bright ribbons tracing every plate boundary with the broad field barely visible between them.
   * The mistake was reasoning about it as "less than half the weight" when the comparison that matters is
   * against the broad field's *spread*, not its range: the faults contribute a deterministic +0.30 where fbm
   * contributes about ±0.15 either side of its median. At 0.15 against a stretched field spanning the whole
   * unit interval, a range reads as somewhat more magical than the plain beside it, which is the intent.
   */
  val faultWeight: Double = 0.15,

  /**
   * How much the finer ridged term adds, measured from its own median.
   *
   * Centred rather than added, because `Noise.ridged` returns `[0,1]` with a median near 0.6 - added raw it
   * would be a constant lift on the whole world plus a little texture, and the constant is exactly the sort
   * of thing that quietly moves a threshold somebody else calibrated.
   */
  val deepWeight: Double = 0.12,

  /** Wavelength of that finer term, in metres. Raw, like [wavelength]. */
  val deepWavelength: Double = 8_000.0
) : Params {

  init {
    require(wavelength > 0.0) { "wavelength must be positive, was $wavelength" }
    require(octaves >= 1) { "octaves must be at least 1, was $octaves" }
    require(warpWavelength > 0.0) { "warpWavelength must be positive, was $warpWavelength" }
    require(warpAmplitude >= 0.0) { "warpAmplitude must not be negative, was $warpAmplitude" }
    require(warpAmplitude < wavelength * 0.5) {
      "warpAmplitude $warpAmplitude folds a field of wavelength $wavelength onto itself"
    }
    require(broadLow < broadHigh) { "broadLow $broadLow must be below broadHigh $broadHigh" }
    require(broadLow >= 0.0 && broadHigh <= 1.0) { "the broad cuts must lie inside [0,1]" }
    require(faultRange > 0.0) { "faultRange must be positive, was $faultRange" }
    require(faultWeight in 0.0..1.0) { "faultWeight must be a share, was $faultWeight" }
    require(deepWeight in 0.0..1.0) { "deepWeight must be a share, was $deepWeight" }
    require(deepWavelength > 0.0) { "deepWavelength must be positive, was $deepWavelength" }
  }

  fun overriddenBy(source: ParamsText.ParamsSource) = copy(
    wavelength = source.double("wavelength", wavelength),
    octaves = source.int("octaves", octaves),
    warpWavelength = source.double("warpWavelength", warpWavelength),
    warpAmplitude = source.double("warpAmplitude", warpAmplitude),
    broadLow = source.double("broadLow", broadLow),
    broadHigh = source.double("broadHigh", broadHigh),
    faultRange = source.double("faultRange", faultRange),
    faultWeight = source.double("faultWeight", faultWeight),
    deepWeight = source.double("deepWeight", deepWeight),
    deepWavelength = source.double("deepWavelength", deepWavelength)
  )

  override fun digest() = ParamsDigest()
    .put("wavelength", wavelength)
    .put("octaves", octaves)
    .put("warpWavelength", warpWavelength)
    .put("warpAmplitude", warpAmplitude)
    .put("broadLow", broadLow)
    .put("broadHigh", broadHigh)
    .put("faultRange", faultRange)
    .put("faultWeight", faultWeight)
    .put("deepWeight", deepWeight)
    .put("deepWavelength", deepWavelength)
}

/**
 * Where the world's mana is, before anybody has done anything about it.
 *
 * ### Why this is separate from the corruption stage
 *
 * Corruption is mana minus what civilisation has quenched, and suppression has to read *standing*
 * settlements, which is only known after the history simulation has burned some and emptied others. But
 * history must also *react* to mana - blight is a thing that happens to a town - and a stage cannot both
 * precede and follow another one.
 *
 * Splitting the field at its natural seam resolves that with no cycle and a better story:
 *
 * ```
 * mana ─→ settlements ─→ history ─→ corruption
 *     └────────────────────↗
 * ```
 *
 * This stage is the **geology**: what the rock holds, unaffected by anybody. History reads it and decides who
 * suffered. [CorruptionStage] then subtracts what the survivors hold back. So a town standing in a high-mana
 * valley suppresses mana *because it fought it*, which is the causal order, and a town history razed stops
 * suppressing - the wilderness takes the ruin back on its own.
 *
 * ### Mana follows the plates
 *
 * The broad field is warped fbm, and it would work alone. It is biased toward convergent plate boundaries
 * anyway, using the same `exp(-distance / range)` falloff `ResourceStage` uses for ore genesis and
 * `HistoryStage` for volcanism, because a world whose magic is an unrelated noise field laid over its
 * geography reads as two maps rather than one place. The bias is weak enough ([ManaParams.faultWeight]) that
 * provinces still appear well away from any boundary - `ManaStageTest` measures that as a *lift* against a
 * control rather than as a bare share, because on a world this size almost every cell is near some boundary
 * and the bare number says nothing.
 *
 * ### The output is a rank over the world's own land, not a raw field
 *
 * The composite is stretched but its realised distribution still moves from seed to seed, and at three cycles
 * across an axis it moves a lot - the median came out at 0.73 on one 128 km world and near 0.5 on another.
 * That is fatal for a layer several unrelated consumers *threshold*: "mana above 0.75" has to mean the same
 * kind of place on every world, or the storm rate, the corrupted share and the spawn levels all wander
 * together and no amount of tuning fixes them.
 *
 * So the last pass replaces each value with its **percentile rank among this world's land cells**, exactly as
 * `BiomeStage.rankConfidence` does and for the same reason. `0.75` then means "stronger than three quarters
 * of the land on this world" by construction, on every seed and at every world size.
 *
 * Ranking is monotone, so it moves no province and changes no shape - it relabels the contours. The cost is
 * the one [LayerId.BIOME_CONFIDENCE] already documents: the value is **relative to a world** and must not be
 * compared across two.
 */
class ManaStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: ManaParams = ManaParams()
) : Stage {

  override val id = ID
  override val version = 1
  override val paramsVersion get() = params.digest().value

  /**
   * Tectonics for the faults, glacial for the final surface, hydrology and biomes for the water.
   *
   * `ELEVATION` is glacial's rather than erosion's, so this reads the ground everything else means when it
   * says elevation - see that layer's KDoc for what happened when a stage read the pre-ice surface instead.
   */
  override val dependencies = listOf(
    TectonicsStage.ID,
    GlacialStage.ID,
    HydrologyStage.ID,
    BiomeStage.ID
  )

  override val scale = StageScale.WORLD

  override val outputs = listOf(StageOutput.Raster(LayerId.MANA_DENSITY))

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val config = ctx.config
    val metres = region.resolution.metresPerCell

    val faultDistance = convergentDistance(ctx, region)

    val fieldSeed = GenRng.hash(config.seed, FIELD_SALT)
    val warpSeed = GenRng.hash(config.seed, WARP_SALT)
    val deepSeed = GenRng.hash(config.seed, DEEP_SALT)

    // Every length here is used raw. See ManaParams.wavelength for why detail scale is the wrong lever for
    // a field whose whole point is how far a player has to walk to leave it.
    val wavelength = params.wavelength
    val warpWavelength = params.warpWavelength
    val warpAmplitude = params.warpAmplitude
    val deepWavelength = params.deepWavelength
    val faultRange = params.faultRange

    val mana = Grid(region.width, region.height)

    // A pure function of position over read-only inputs, so the rows split with no accumulator between them.
    Parallel.rows(region.height, region.width) { yFrom, yUntil ->
      for (y in yFrom until yUntil) {
        for (x in 0 until region.width) {
          val worldX = (region.minX + x + 0.5) * metres
          val worldY = (region.minY + y + 0.5) * metres

          val warped = Noise.warp(
            warpSeed, worldX, worldY, warpAmplitude, 1.0 / warpWavelength, WARP_OCTAVES
          )

          val broad = 0.5 * (1.0 + Noise.fbm(
            fieldSeed, warped[0] / wavelength, warped[1] / wavelength, params.octaves
          ))

          // Stretched before anything is added to it, so the provinces are the structure and the two terms
          // below are corrections to it. Adding to the raw fbm instead is what made the first version a map
          // of the fault network - see ManaParams.broadLow.
          val stretched = smoothstep(params.broadLow, params.broadHigh, broad)

          val arc = exp(-faultDistance.data[y * region.width + x] / faultRange)

          val deep = Noise.ridged(
            deepSeed, worldX / deepWavelength, worldY / deepWavelength, DEEP_OCTAVES
          )

          val value = stretched +
              params.faultWeight * arc +
              params.deepWeight * (deep - DEEP_MEDIAN)

          mana.data[y * region.width + x] = value.coerceIn(0.0, 1.0)
        }
      }
    }

    rankAgainstLand(ctx, region, mana)

    return StageResult.of(mana.toLayer(LayerId.MANA_DENSITY, region))
  }

  /**
   * Replaces every value with its percentile rank among the **land** cells, in place.
   *
   * Land only, because the land is what the thresholds are about and because the ocean share swings from 15%
   * to 95% across legitimate seeds - ranking over every cell would make the same hillside read strong on a
   * watery world and weak on a dry one, which is precisely the instability this pass exists to remove.
   *
   * Ocean cells are ranked on the same land-derived curve rather than excluded, so the layer stays one
   * monotone function of the composite everywhere and a reader that does not care about the shoreline does
   * not have to know where it is.
   */
  private fun rankAgainstLand(ctx: GenContext, region: CellRegion, mana: Grid) {
    val elevation = Grid.from(ctx.layers.float(LayerId.ELEVATION))
    val waterLevel = Grid.from(ctx.layers.float(LayerId.WATER_LEVEL))
    val biome = ctx.layers.int(LayerId.BIOME)
    val seaLevel = ctx.config.seaLevel

    val land = ArrayList<Double>(mana.data.size / 2)
    for (i in mana.data.indices) {
      if (isStandableLand(elevation, waterLevel, biome, region, seaLevel, i)) land.add(mana.data[i])
    }

    // A world with no land at all is not one this pipeline produces - `checkLandFraction` floors it at 5% -
    // but a partial pipeline in a test can, and a rank over an empty set has no answer. Fall back to every
    // cell rather than dividing by zero.
    val reference = if (land.isEmpty()) mana.data.toMutableList() else land
    reference.sort()

    val sorted = reference.toDoubleArray()
    val last = (sorted.size - 1).coerceAtLeast(1)

    for (i in mana.data.indices) {
      mana.data[i] = (upperBound(sorted, mana.data[i]).toDouble() / last).coerceAtMost(1.0)
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

  /**
   * Distance to the nearest convergent plate boundary, rasterised from the fault polylines.
   *
   * Read from the vector tier rather than re-derived from `plate_id`, for the reason `ResourceStage` gives
   * where it does the same thing: re-deriving means this stage picking its own tie-break for where a boundary
   * runs, and then two stages disagreeing about the location of the same fault.
   */
  private fun convergentDistance(ctx: GenContext, region: CellRegion): Grid {
    val metres = region.resolution.metresPerCell
    val onBoundary = BooleanArray(region.width * region.height)

    for (feature in ctx.features.query(region.toWorld())) {
      if (feature.kind != FeatureKind.FAULT) continue
      val fault = feature as? MarkerFeature ?: continue

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
      // A world with no faults at all is a legitimate seed, not a crash; cap so nothing sees MAX_VALUE.
      val cap = max(region.width, region.height) * metres
      for (i in grid.data.indices) {
        if (grid.data[i] > cap) grid.data[i] = cap
      }
    }
  }

  companion object {
    val ID = StageId("mana")

    /** Octaves of the warp. Two, as everywhere else that warps a broad field. */
    private const val WARP_OCTAVES = 2

    /** Octaves of the hot-spot term. Ridged noise crests hard, so it needs few. */
    private const val DEEP_OCTAVES = 3

    private const val FIELD_SALT = 0x4D616E6146696C64L
    private const val WARP_SALT = 0x4D616E6157617270L
    private const val DEEP_SALT = 0x4D616E6144656570L

    /**
     * Median of `Noise.ridged` at three octaves, measured: 0.617 on the reference world.
     *
     * Subtracted so the hot-spot term is a signed correction rather than a constant lift plus texture.
     */
    private const val DEEP_MEDIAN = 0.6

    /** Hermite smoothstep. Zero derivative at both ends, so a stretched field gains no crease. */
    private fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
      val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
      return t * t * (3.0 - 2.0 * t)
    }
  }
}

/**
 * True where a cell is ground somebody could stand on: above sea level and not under a lake.
 *
 * Shared by [ManaStage]'s downstream consumers rather than rewritten per caller, because "land" is the
 * denominator of the corrupted-share target and two definitions of it would make that target mean two things.
 * The lake test is the one that is easy to forget: `ELEVATION > seaLevel` is true of a lake bed.
 *
 * [biome] is nullable so that a stage running **before** `BiomeStage` can still share this rather than write
 * its own. `VolcanismStage` is the case: it has to rank its field over land, and it cannot depend on the biomes
 * because the biomes depend on it. Passing null drops only the third test, which is belt-and-braces - given the
 * first two, a cell above sea level with no water level over it is not classified as ocean or lake anyway - so
 * the two answers agree everywhere, and the one definition stays in one place.
 */
internal fun isStandableLand(
  elevation: Grid,
  waterLevel: Grid,
  biome: net.bestia.worldgen.core.IntLayer?,
  region: CellRegion,
  seaLevel: Double,
  index: Int
): Boolean {
  if (elevation.data[index] <= seaLevel) return false
  if (!waterLevel.data[index].isNaN()) return false
  if (biome == null) return true

  val x = region.minX + index % region.width
  val y = region.minY + index / region.width
  return !Biome.entries[biome[x, y]].isWater
}
