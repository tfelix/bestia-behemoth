package net.bestia.worldgen.voxel

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.ParamsText
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.vector.Quantize

/** Tuning for [CrystalScatter]. */
data class CrystalParams(

  /**
   * Edge of the lattice square that holds at most one crystal, in metres.
   *
   * The **decision unit**, and it has to be larger than a voxel for the reason `TODO.md` states outright: a
   * smooth probability sampled per column is still a coin flip per column, and a coin flip per column is
   * speckle rather than a crystal field. Near the tree lattice's scale so the two scatters interleave
   * rather than fight over the same ground.
   */
  val cellSize: Double = 11.0,

  /**
   * How far a crystal may wander from its cell's centre, as a share of [cellSize].
   *
   * Copied from `VegetationParams.jitterShare` deliberately - it is the same trade, between a visible grid
   * at zero and two objects on one spot at one.
   */
  val jitterShare: Double = 0.7,

  /**
   * Mana below which nothing grows at all.
   *
   * The density control, and it is a *cutoff* rather than a taper so that low-mana country is genuinely
   * empty and a crystal field is somewhere a player recognises. `VegetationParams.clearingCutoff` plays the
   * same role for woods, for the same reason.
   */
  val manaFloor: Double = 0.35,

  /**
   * Chance a lattice cell holds a crystal on clean ground at full mana.
   *
   * **Measured at 48 per square kilometre**, one every 145 m or so - "you come across one occasionally",
   * which is what "small deposits on uncorrupted land too" has to mean if it is not to become scenery a
   * player walks past. `CrystalScatterTest` is where the figure comes from; the nominal rate this implies is
   * a little higher, and the difference is the ground that turns out to be water, ice or bare rock.
   */
  val cleanLandDensity: Double = 0.004,

  /**
   * Chance a lattice cell holds a crystal on fully corrupted ground.
   *
   * **Measured at 789 per square kilometre in the interior of a province**, one every 36 m: the ground is
   * visibly growing crystal. Sixteen times the clean rate, which is what makes the two read as different
   * countries rather than as more of the same.
   *
   * Measured in the *interior* deliberately, and the distinction is worth keeping: corruption is sampled
   * bilinearly, so the same tuning yields about a third of this on the fringe of a province where the field
   * is already blending toward clean. That gradient is the point - a player walks into a crystal field
   * rather than stepping over a line into one.
   */
  val corruptedDensity: Double = 0.12,

  /** Share of crystals that are the tall variant on fully corrupted ground; near zero on clean. */
  val largeShare: Double = 0.12,

  /** Shortest a crystal stands, in metres. */
  val minHeight: Double = 1.0,

  /** Tallest a crystal stands, in metres. Above about four the one-column simplification starts to show. */
  val maxHeight: Double = 3.5
) : Params {

  init {
    require(cellSize > 0.0) { "cellSize must be positive, was $cellSize" }
    require(jitterShare in 0.0..1.0) { "jitterShare must be in [0,1], was $jitterShare" }
    require(manaFloor in 0.0..1.0) { "manaFloor must be in [0,1], was $manaFloor" }
    require(cleanLandDensity in 0.0..1.0) { "cleanLandDensity must be in [0,1], was $cleanLandDensity" }
    require(corruptedDensity in 0.0..1.0) { "corruptedDensity must be in [0,1], was $corruptedDensity" }
    require(largeShare in 0.0..1.0) { "largeShare must be in [0,1], was $largeShare" }
    require(minHeight > 0.0) { "minHeight must be positive, was $minHeight" }
    require(minHeight <= maxHeight) { "minHeight $minHeight exceeds maxHeight $maxHeight" }
  }

  fun overriddenBy(source: ParamsText.ParamsSource) = copy(
    cellSize = source.double("cellSize", cellSize),
    jitterShare = source.double("jitterShare", jitterShare),
    manaFloor = source.double("manaFloor", manaFloor),
    cleanLandDensity = source.double("cleanLandDensity", cleanLandDensity),
    corruptedDensity = source.double("corruptedDensity", corruptedDensity),
    largeShare = source.double("largeShare", largeShare),
    minHeight = source.double("minHeight", minHeight),
    maxHeight = source.double("maxHeight", maxHeight)
  )

  override fun digest() = ParamsDigest()
    .put("cellSize", cellSize)
    .put("jitterShare", jitterShare)
    .put("manaFloor", manaFloor)
    .put("cleanLandDensity", cleanLandDensity)
    .put("corruptedDensity", corruptedDensity)
    .put("largeShare", largeShare)
    .put("minHeight", minHeight)
    .put("maxHeight", maxHeight)
}

/**
 * Mana crystals, growing out of the ground.
 *
 * Implicit, like the trees and for the same reason: they are small, there are a great many of them, and a
 * feature per crystal would be storage for something a hash answers. `voxel/VegetationScatter.kt` makes the
 * argument at one order of magnitude up and it holds here too - identity, when a runtime eventually needs one
 * to know that a player harvested this crystal and not that one, is the **lattice cell**, which is stable,
 * free and the same answer the harvestable-tree work will need.
 *
 * ### Much simpler than the tree scatter, deliberately
 *
 * A crystal is **one column wide and about three metres tall**, so the two things that make the tree scatter
 * expensive do not arise: no crown reaches across a chunk border, and the ground it stands on is the ground
 * of the column being drawn rather than of some trunk outside it. So there is no halo, no candidate buffer,
 * no `searchCells`, and `columnAt` needs nothing from its neighbours.
 *
 * Do not "fix" this by copying the tree code. The halo there is not boilerplate - it is what a four-metre
 * crown costs, and a crystal does not cost it.
 *
 * ### Where they are
 *
 * Density interpolates from [CrystalParams.cleanLandDensity] to [CrystalParams.corruptedDensity] with the
 * corruption, gated below [CrystalParams.manaFloor] so quiet country is empty. That is what makes small
 * deposits findable on clean ground - a low-level player can collect them - while a corrupted province is
 * carpeted.
 */
class CrystalScatter(
  config: WorldConfig,
  private val surface: SurfaceSampler,
  seed: Long,
  private val params: CrystalParams
) {

  private val cellUnits = Quantize.toFixed(params.cellSize)
  private val voxelUnits = Quantize.toFixed(config.voxelSize)
  private val scatterSeed = GenRng.mix64(seed xor SCATTER_SALT)

  /**
   * Adds the crystal standing on this column, if there is one.
   *
   * Never under water and never on ice or snow: a crystal grows out of ground, and the cap block is the one
   * place that knows what the ground is made of - the same test `VegetationScatter.plant` uses for a trunk.
   */
  fun columnAt(worldX: Double, worldY: Double, ground: Double, into: StructureSpans) {
    val cellX = Math.floorDiv(Quantize.toFixed(worldX), cellUnits)
    val cellY = Math.floorDiv(Quantize.toFixed(worldY), cellUnits)

    val mana = surface.manaAt(worldX, worldY)
    if (mana < params.manaFloor) return

    // The crystal's own position inside its cell, and then the column it lands in. Only the column that
    // *owns* the crystal draws it, so a crystal is one voxel column wherever it is asked about from.
    val hash = GenRng.hash(scatterSeed, cellX, cellY)
    val jitterX = jitter(hash, JITTER_X_SALT)
    val jitterY = jitter(hash, JITTER_Y_SALT)

    // Back to metres off the fixed-point lattice, not from `worldX` - the cell's own origin has to be the
    // same number for every column in it, or the crystal moves as you ask about it from different voxels.
    val originX = cellX * cellUnits / Quantize.PER_METRE
    val originY = cellY * cellUnits / Quantize.PER_METRE
    val crystalX = originX + (0.5 + jitterX) * params.cellSize
    val crystalY = originY + (0.5 + jitterY) * params.cellSize

    if (Math.floorDiv(Quantize.toFixed(crystalX), voxelUnits) !=
      Math.floorDiv(Quantize.toFixed(worldX), voxelUnits)
    ) return
    if (Math.floorDiv(Quantize.toFixed(crystalY), voxelUnits) !=
      Math.floorDiv(Quantize.toFixed(worldY), voxelUnits)
    ) return

    // Corruption at the crystal, not at the column being drawn. They are the same column here, but saying so
    // explicitly is what keeps this correct if the cell ever grows past one voxel.
    val corruption = surface.corruptionAt(crystalX, crystalY)
    val density = params.cleanLandDensity +
        (params.corruptedDensity - params.cleanLandDensity) * corruption
    if (GenRng.unit(GenRng.mix64(hash xor PRESENCE_SALT)) >= density) return

    if (surface.waterLevelAt(crystalX, crystalY) > ground) return

    val cap = SurfaceCover.cap(
      surface.biomeAt(crystalX, crystalY),
      surface.temperatureAt(crystalX, crystalY),
      0.0,
      surface.isBlightedAt(crystalX, crystalY)
    )
    if (cap == BlockType.ICE || cap == BlockType.SNOW) return

    val sizeRoll = GenRng.unit(GenRng.mix64(hash xor SIZE_SALT))
    val large = sizeRoll < params.largeShare * corruption
    val heightRoll = GenRng.unit(GenRng.mix64(hash xor HEIGHT_SALT))
    val height = params.minHeight + heightRoll * (params.maxHeight - params.minHeight)

    into.add(
      ground,
      ground + if (large) height else height * SMALL_HEIGHT_SHARE,
      if (large) BlockType.MANA_CRYSTAL_LARGE else BlockType.MANA_CRYSTAL_SMALL
    )
  }

  /** A jitter in `[-jitterShare/2, +jitterShare/2]` from a slice of the cell's hash. */
  private fun jitter(hash: Long, salt: Long): Double =
    (GenRng.unit(GenRng.mix64(hash + salt)) - 0.5) * params.jitterShare

  private companion object {
    const val SCATTER_SALT = 0x43727973746C7331L
    const val PRESENCE_SALT = 0x1E3779B97F4A7C15L
    const val SIZE_SALT = 0x6A09E667F3BCC909L
    const val HEIGHT_SALT = 0x3B67AE8584CAA73BL

    const val JITTER_X_SALT = 0x11L
    const val JITTER_Y_SALT = 0x1FL

    /** How tall a small crystal is against a large one. */
    const val SMALL_HEIGHT_SHARE = 0.45
  }
}
