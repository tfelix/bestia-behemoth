package net.bestia.worldgen.voxel

import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.LayerStore
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.fields.Noise
import kotlin.math.floor
import kotlin.math.pow

/** Tuning for [Stratigraphy]. */
data class StrataParams(

  /** Sedimentary cover over the basement in a deep basin, in metres. */
  val maxCoverThickness: Double = 620.0,

  /** Thinnest and thickest a single bed can be, in metres. */
  val minBedThickness: Double = 9.0,
  val maxBedThickness: Double = 38.0,

  /** Wavelength over which bed thickness varies, in metres. */
  val bedWavelength: Double = 9_000.0,

  /** Amplitude of the structural warp that tilts and folds the beds, in metres. */
  val foldAmplitude: Double = 170.0,

  /** Wavelength of that warp. Long, because folding is a regional thing. */
  val foldWavelength: Double = 14_000.0,

  /** Depth below sea level at which the basement is oceanic basalt rather than granite. */
  val oceanicBasementDepth: Double = 500.0
) : Params {

  init {
    require(maxCoverThickness >= 0.0) { "maxCoverThickness must not be negative, was $maxCoverThickness" }
    require(minBedThickness > 0.0) { "minBedThickness must be positive, was $minBedThickness" }
    require(minBedThickness <= maxBedThickness) {
      "minBedThickness $minBedThickness is thicker than maxBedThickness $maxBedThickness"
    }
    require(bedWavelength > 0.0) { "bedWavelength must be positive, was $bedWavelength" }
    require(foldAmplitude >= 0.0) { "foldAmplitude must not be negative, was $foldAmplitude" }
    require(foldWavelength > 0.0) { "foldWavelength must be positive, was $foldWavelength" }
    require(oceanicBasementDepth.isFinite()) { "oceanicBasementDepth must be finite, was $oceanicBasementDepth" }
  }

  override fun digest() = ParamsDigest()
    .put("maxCoverThickness", maxCoverThickness)
    .put("minBedThickness", minBedThickness)
    .put("maxBedThickness", maxBedThickness)
    .put("bedWavelength", bedWavelength)
    .put("foldAmplitude", foldAmplitude)
    .put("foldWavelength", foldWavelength)
    .put("oceanicBasementDepth", oceanicBasementDepth)
}

/**
 * The rock stack under one voxel column: what a shaft sunk here would pass through.
 *
 * A per-column object rather than a `rockAt(x, y, z)` function, and that is a performance decision with
 * a factor of a few hundred in it. Everything except the elevation - the coarse height, the rock
 * hardness, the structural datum, the bed thickness, the plate - is constant down a column, and all of
 * them cost a raster interpolation. Evaluating them per *voxel* means 256 bicubic samples per column and
 * a quarter of a million per chunk, which makes materialisation slower than every other stage of the
 * pipeline combined. Evaluating them once per column leaves one hash per voxel.
 *
 * It is also the shape the architecture document asks for: a vertical stack of layer descriptors.
 */
class RockColumn internal constructor(
  /** Absolute elevation of the top of the crystalline basement. */
  val basementTop: Double,
  val basementRock: BlockType,
  /** Elevation the bed sequence is measured from, warped by folding. */
  private val datum: Double,
  private val bedThickness: Double,
  private val plate: Int,
  private val faciesSalt: Long
) {

  fun rockAt(elevation: Double): BlockType {
    if (elevation <= basementTop) return basementRock
    return faciesOf(bedIndexAt(elevation))
  }

  fun bedIndexAt(elevation: Double): Int = floor((elevation - datum) / bedThickness).toInt()

  /** Elevation of the top face of bed [bed], for filling a whole bed as one run. */
  fun topOfBed(bed: Int): Double = datum + (bed + 1) * bedThickness

  /**
   * Which sedimentary rock a numbered bed is made of.
   *
   * A weighted draw from a hash rather than a fixed repeating sequence: a repeating pattern is legible as
   * a pattern the second time a player digs, and there is no reason for the world to have one. Keyed on
   * the plate so that each has its own stratigraphic column - a geologically real unit with irregular
   * boundaries, unlike a spatial grid, which would leave a visible rectangular seam.
   */
  fun faciesOf(bed: Int): BlockType {
    val roll = GenRng.hashUnit(faciesSalt, plate.toLong(), bed.toLong())
    return when {
      roll < 0.34 -> BlockType.SHALE
      roll < 0.63 -> BlockType.SANDSTONE
      roll < 0.90 -> BlockType.LIMESTONE
      else -> BlockType.CONGLOMERATE
    }
  }
}

/**
 * Rock stratigraphy as a function of world position.
 *
 * The load-bearing decision here is that **beds are defined in absolute elevation, warped by a smooth
 * structural datum - not as depths below the surface.** Depth-below-surface beds follow the topography,
 * so every hillside shows the same layer at the same depth and a canyon wall shows bands running parallel
 * to its rim. Beds in absolute elevation get *truncated* by the topography instead, which is what real
 * strata do, and it produces cap-rock mesas and banded gorges without either being modelled explicitly.
 *
 * Everything is a pure function of world position, so two chunks either side of a border agree about
 * which rock is where without any communication.
 */
class Stratigraphy(
  private val coarseElevation: FloatLayer,
  private val hardness: FloatLayer,
  private val plateId: IntLayer,
  seed: Long,
  private val seaLevel: Double = 0.0,
  private val params: StrataParams = StrataParams()
) {

  private val bedSeed = GenRng.mix64(seed xor BED_SALT)
  private val foldSeed = GenRng.mix64(seed xor FOLD_SALT)
  private val faciesSalt = GenRng.mix64(seed xor FACIES_SALT)

  fun columnAt(worldX: Double, worldY: Double): RockColumn {
    val coarse = coarseElevation.sampleBicubic(worldX, worldY)
    val rock = hardness.sampleBilinear(worldX, worldY).coerceIn(0.0, 1.0)

    return RockColumn(
      basementTop = coarse - coverThicknessAt(worldX, worldY, rock),
      basementRock = if (coarse < seaLevel - params.oceanicBasementDepth) {
        BlockType.BASALT
      } else {
        BlockType.GRANITE
      },
      datum = Noise.fbm(
        foldSeed, worldX / params.foldWavelength, worldY / params.foldWavelength, FOLD_OCTAVES
      ) * params.foldAmplitude,
      bedThickness = bedThicknessAt(worldX, worldY),
      plate = plateId.sampleNearest(worldX, worldY),
      faciesSalt = faciesSalt
    )
  }

  /**
   * Metres of sedimentary cover over the basement.
   *
   * Driven by rock hardness, which the tectonics stage already lowered in quiet continental lowlands - so
   * basins get deep cover and shields get almost none, which is the correct relationship and needed no
   * extra layer to express.
   */
  fun coverThicknessAt(worldX: Double, worldY: Double, hardnessAt: Double): Double {
    val softness = (1.0 - hardnessAt).coerceIn(0.0, 1.0)
    val variation = Noise.fbm(
      bedSeed, worldX / (params.bedWavelength * 2.0), worldY / (params.bedWavelength * 2.0), 2
    )
    return (params.maxCoverThickness * softness.pow(1.6) * (1.0 + variation * 0.35)).coerceAtLeast(0.0)
  }

  fun bedThicknessAt(worldX: Double, worldY: Double): Double {
    val t = (Noise.fbm(
      bedSeed, worldX / params.bedWavelength, worldY / params.bedWavelength, 2
    ) + 1.0) * 0.5
    return params.minBedThickness + (params.maxBedThickness - params.minBedThickness) * t
  }

  companion object {

    /**
     * The rock under a generated world, from the layers that decide it.
     *
     * The point of a factory rather than three `require` calls at each call site: **anything that wants to know
     * where a rock type is has to agree with the materialiser about it, and the only way to guarantee that is
     * for both to build the same object from the same layers.** Two sibling packages each spelling out
     * `ELEVATION`, `ROCK_HARDNESS`, `PLATE_ID`, the seed and the sea level would agree today and diverge the
     * first time one of them gained a fourth input - and the divergence would be a stage that thinks the
     * limestone is somewhere the voxels do not put it, which reads as a bug in whatever consumed the stage.
     */
    fun of(layers: LayerStore, config: WorldConfig, params: StrataParams = StrataParams()) = Stratigraphy(
      coarseElevation = layers.require(LayerId.ELEVATION),
      hardness = layers.require(LayerId.ROCK_HARDNESS),
      plateId = layers.require(LayerId.PLATE_ID),
      seed = config.seed,
      seaLevel = config.seaLevel,
      params = params
    )

    private const val BED_SALT = 0x1D9C4A7E35B2F80L
    private const val FOLD_SALT = 0x5B27E1D93C4A6F0L
    private const val FACIES_SALT = 0x38F5A2C7E91D46BL

    private const val FOLD_OCTAVES = 3
  }
}
