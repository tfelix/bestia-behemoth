package net.bestia.worldgen.voxel

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.fields.Noise
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointFeature
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Quantize
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature

/** Tuning for [LootScatter]. */
data class LootScatterParams(

  /**
   * Lattice edges, in metres, one per kind.
   *
   * One lattice per kind rather than one shared like [GroundCoverScatter]'s, because these are *not* mutually
   * exclusive: a stick and a lump of resin lie under a tree on stony ground beside a boulder. A shared lattice would make them
   * compete for cells and thin each other wherever two belong.
   *
   * **Not a thing to change after a world ships**: a cell index is the durable half of a prop's identity.
   */
  val boulderCellSize: Double = 9.0,
  val stoneCellSize: Double = 7.0,
  val stickCellSize: Double = 6.0,
  val resinCellSize: Double = 8.0,

  /** How far an item may wander from its cell's centre, as a share of its cell. */
  val jitterShare: Double = 0.7,

  /**
   * Chance per cell of a boulder on the stoniest ground there is, before the patch field.
   *
   * Stoniness is the product of thin soil, a rocky biome and glacial ground, so this is the rate on a scree of
   * an alpine moraine and a small share of it is what a grassland gets.
   */
  val boulderDensity: Double = 0.12,

  /** Floor on the boulder rate anywhere dry, as a share of [boulderDensity]: the odd erratic in a meadow. */
  val boulderBaseline: Double = 0.025,

  /** Chance per cell of a loose stone on the stoniest ground, before the patch field. */
  val stoneDensity: Double = 0.14,

  /**
   * Floor on the loose stone rate anywhere dry, as a share of [stoneDensity].
   *
   * Higher than the boulder's, deliberately: a loose stone is a first crafting material, and a new player
   * standing in a meadow beside the spawn town has to find one within a short walk.
   */
  val stoneBaseline: Double = 0.18,

  /** Chance per cell of a stick under a closed canopy, scaled by [Biome.canopy]. */
  val stickDensity: Double = 0.09,

  /**
   * Chance per cell of a lump of resin under a closed conifer canopy, scaled by [Biome.canopy] and by how much
   * of that canopy is conifer. A third of the stick rate: the design promises a few lumps, not a carpet.
   */
  val resinDensity: Double = 0.03,

  /** Soil depth in metres at and above which the soil adds nothing to how stony the ground is. */
  val stonySoilDepth: Double = 0.8,

  /** Metres either side of a moraine or glacial trough axis over which the ground counts as glacial. */
  val glacialReach: Double = 250.0,

  /** Wavelength of the patch field, in metres: how far across a boulder field or a stony patch is. */
  val patchWavelength: Double = 60.0,

  /** Patch value below which the patch term is at its floor. */
  val patchCutoff: Double = 0.3,

  /** Width of the ramp above [patchCutoff]. */
  val patchSoftness: Double = 0.35,

  /** Nominal sizes in metres. A boulder is one size with several models; a stone and a stick are fixed. */
  val boulderHeight: Double = 1.6,
  val boulderRadius: Double = 1.2,
  val stoneHeight: Double = 0.15,
  val stickHeight: Double = 0.1,
  val resinHeight: Double = 0.08
) : Params {

  init {
    require(boulderCellSize > 0.0 && stoneCellSize > 0.0 && stickCellSize > 0.0 && resinCellSize > 0.0) {
      "lattice sizes must be positive"
    }
    require(jitterShare in 0.0..1.0) { "jitterShare must be in [0,1], was $jitterShare" }
    require(
      boulderDensity in 0.0..1.0 && stoneDensity in 0.0..1.0 && stickDensity in 0.0..1.0 && resinDensity in 0.0..1.0
    ) {
      "densities must be in [0,1]"
    }
    require(boulderBaseline in 0.0..1.0 && stoneBaseline in 0.0..1.0) { "baselines must be in [0,1]" }
    require(stonySoilDepth > 0.0) { "stonySoilDepth must be positive, was $stonySoilDepth" }
    require(glacialReach >= 0.0) { "glacialReach must not be negative, was $glacialReach" }
    require(patchWavelength > 0.0) { "patchWavelength must be positive, was $patchWavelength" }
    require(patchSoftness > 0.0) { "patchSoftness must be positive, was $patchSoftness" }
  }

  override fun digest(): ParamsDigest = ParamsDigest()
    .put("boulderCellSize", boulderCellSize)
    .put("stoneCellSize", stoneCellSize)
    .put("stickCellSize", stickCellSize)
    .put("resinCellSize", resinCellSize)
    .put("jitterShare", jitterShare)
    .put("boulderDensity", boulderDensity)
    .put("boulderBaseline", boulderBaseline)
    .put("stoneDensity", stoneDensity)
    .put("stoneBaseline", stoneBaseline)
    .put("stickDensity", stickDensity)
    .put("resinDensity", resinDensity)
    .put("stonySoilDepth", stonySoilDepth)
    .put("glacialReach", glacialReach)
    .put("patchWavelength", patchWavelength)
    .put("patchCutoff", patchCutoff)
    .put("patchSoftness", patchSoftness)
    .put("boulderHeight", boulderHeight)
    .put("boulderRadius", boulderRadius)
    .put("stoneHeight", stoneHeight)
    .put("stickHeight", stickHeight)
    .put("resinHeight", resinHeight)
}

/**
 * The things lying on the ground that a player can take or break: boulders, loose stones, fallen sticks and resin.
 *
 * Implicit, like every other scatter in this package, for the reason [VegetationScatter] gives at length - a
 * hash answers where they are and nothing stores them. A stone picked up or a boulder broken is a divergence
 * row against its [PropId], exactly like a felled tree.
 *
 * ### Where they lie
 *
 * - **Boulders** follow how stony the ground is: thin soil (steep ground and bare rock keep little), a rocky
 *   biome, and glacial ground - a boulder field on a moraine is the ice's own litter, an erratic it carried and
 *   dropped. A meadow still gets the odd one, at [LootScatterParams.boulderBaseline].
 * - **Loose stones** follow the same stoniness with a much higher floor, so there are always some within a
 *   short walk of anywhere dry - they are the first thing a new player crafts with.
 * - **Sticks** lie under trees, by [Biome.canopy], and nowhere else.
 * - **Resin** lies under conifers. There are no tree species, so the biome stands in for them: see [coniferShareOf].
 *
 * Nothing is placed on ground somebody works or keeps: a field, a settlement's graded disc, a cemetery.
 * Paving, buildings, bridges and cave mouths are refused already by the shared [PropSite].
 */
class LootScatter(
  private val config: WorldConfig,
  private val surface: SurfaceSampler,
  seed: Long,
  private val params: LootScatterParams = LootScatterParams()
) {

  private val boulderSeed = GenRng.mix64(seed xor BOULDER_SALT)
  private val stoneSeed = GenRng.mix64(seed xor STONE_SALT)
  private val stickSeed = GenRng.mix64(seed xor STICK_SALT)
  private val resinSeed = GenRng.mix64(seed xor RESIN_SALT)
  private val patchSeed = GenRng.mix64(seed xor PATCH_SALT)

  /**
   * The items lying inside one chunk.
   *
   * @param nearby the chunk's feature query, for the glacial ground that makes a boulder field and the worked
   *   ground (fields, graded settlement discs, cemeteries) that keeps one clear.
   */
  fun propsIn(chunk: ChunkPos, site: PropSite, nearby: List<VectorFeature>, into: PropInstances) {
    val ground = Ground(nearby)
    lattice(chunk, site, ground, into, PropKind.BOULDER, params.boulderCellSize, boulderSeed)
    lattice(chunk, site, ground, into, PropKind.LOOSE_STONE, params.stoneCellSize, stoneSeed)
    lattice(chunk, site, ground, into, PropKind.STICK, params.stickCellSize, stickSeed)
    lattice(chunk, site, ground, into, PropKind.RESIN, params.resinCellSize, resinSeed)
  }

  /** The chance one cell of [kind]'s lattice at this position holds one, in `[0, 1]`. */
  fun densityAt(kind: PropKind, worldX: Double, worldY: Double, glacial: Double = 0.0): Double {
    val biome = surface.biomeAt(worldX, worldY)
    if (biome == Biome.OCEAN || biome == Biome.LAKE || biome == Biome.ICE_SHEET) return 0.0

    return when (kind) {
      PropKind.BOULDER -> {
        val stony = stoninessAt(biome, worldX, worldY, glacial)
        params.boulderDensity * (params.boulderBaseline + (1.0 - params.boulderBaseline) * stony * patchAt(worldX, worldY))
      }
      PropKind.LOOSE_STONE -> {
        val stony = stoninessAt(biome, worldX, worldY, glacial)
        params.stoneDensity * (params.stoneBaseline + (1.0 - params.stoneBaseline) * stony * patchAt(worldX, worldY))
      }
      PropKind.STICK -> params.stickDensity * biome.canopy
      PropKind.RESIN -> params.resinDensity * biome.canopy * coniferShareOf(biome)
      else -> 0.0
    }
  }

  /**
   * How stony the ground is, in `[0, 1]`: the larger of thin soil, a rocky biome, and glacial ground.
   *
   * The larger rather than a product, because any one of them is a reason on its own. Bare rock in a forest is
   * stony; a moraine under deep soil is stony; an alpine meadow is stony whatever the soil.
   */
  private fun stoninessAt(biome: Biome, worldX: Double, worldY: Double, glacial: Double): Double {
    val thin = 1.0 - PolylineFeature.smoothstep(surface.soilDepthAt(worldX, worldY) / params.stonySoilDepth)
    val rocky = rockinessOf(biome)
    return maxOf(thin, rocky, glacial).coerceIn(0.0, 1.0)
  }

  private fun rockinessOf(biome: Biome): Double = when (biome) {
    Biome.ALPINE, Biome.COLD_DESERT -> 0.9
    Biome.TUNDRA, Biome.DESERT -> 0.6
    Biome.DRYLAND -> 0.45
    else -> 0.1
  }

  /**
   * How much of a biome's canopy is conifer, in `[0, 1]`.
   *
   * Taiga is the boreal forest of spruce, pine and fir. A temperate rainforest is mostly conifer too - cedar,
   * hemlock, the big firs - and a temperate forest is mixed woods with some pine. The temperate two matter more
   * than their shares suggest: most taiga lies below the snow line, where no tree stands and nothing lies.
   */
  private fun coniferShareOf(biome: Biome): Double = when (biome) {
    Biome.TAIGA -> 1.0
    Biome.TEMPERATE_RAINFOREST -> 0.6
    Biome.TEMPERATE_FOREST -> 0.25
    else -> 0.0
  }

  private fun patchAt(worldX: Double, worldY: Double): Double {
    val raw = (Noise.fbm(
      patchSeed, worldX / params.patchWavelength, worldY / params.patchWavelength, PATCH_OCTAVES
    ) + 1.0) * 0.5
    return PolylineFeature.smoothstep((raw - params.patchCutoff) / params.patchSoftness)
  }

  private fun lattice(
    chunk: ChunkPos,
    site: PropSite,
    ground: Ground,
    into: PropInstances,
    kind: PropKind,
    cellSize: Double,
    latticeSeed: Long
  ) {
    val cellUnits = Quantize.toFixed(cellSize)
    fun cellOf(world: Double) = Math.floorDiv(Quantize.toFixed(world), cellUnits)

    val bounds = config.chunkBounds(chunk)
    val jitter = cellSize * params.jitterShare
    val ceiling = when (kind) {
      PropKind.BOULDER -> params.boulderDensity
      PropKind.LOOSE_STONE -> params.stoneDensity
      PropKind.STICK -> params.stickDensity
      else -> params.resinDensity
    }

    for (cellY in cellOf(bounds.minY) until cellOf(bounds.maxY) + 1) {
      for (cellX in cellOf(bounds.minX) until cellOf(bounds.maxX) + 1) {
        val key = GenRng.hash(latticeSeed, cellX, cellY)
        val roll = GenRng.unit(key)
        // Cheap reject: no density here may exceed its kind's own rate.
        if (roll >= ceiling) continue

        val x = (cellX + 0.5) * cellSize + (GenRng.unit(GenRng.mix64(key + 1)) - 0.5) * jitter
        val y = (cellY + 0.5) * cellSize + (GenRng.unit(GenRng.mix64(key + 2)) - 0.5) * jitter
        if (config.chunkOf(x) != chunk.x || config.chunkOf(y) != chunk.y) continue

        if (ground.isWorked(x, y)) continue
        val glacial = if (kind in UNDER_TREES) 0.0 else ground.glacialAt(x, y, params.glacialReach)
        if (roll >= densityAt(kind, x, y, glacial)) continue

        val height = site.groundAt(x, y)
        if (height.isNaN()) continue
        if (surface.waterLevelAt(x, y) > height) continue

        val biome = surface.biomeAt(x, y)
        val blighted = surface.isBlightedAt(x, y)
        val cap = SurfaceCover.cap(biome, surface.coverTemperatureAt(x, y), 0.0, blighted)
        if (cap == BlockType.ICE || cap == BlockType.SNOW) continue

        val (heightM, radiusM) = when (kind) {
          PropKind.BOULDER -> params.boulderHeight to params.boulderRadius
          PropKind.LOOSE_STONE -> params.stoneHeight to 0.0
          PropKind.STICK -> params.stickHeight to 0.0
          else -> params.resinHeight to 0.0
        }

        into.add(
          kind = kind,
          identity = PropId.of(kind, cellX, cellY),
          x = x,
          y = y,
          ground = height,
          heightM = heightM,
          radiusM = radiusM,
          flags = if (blighted) PropFlags.BLIGHTED else 0
        )
      }
    }
  }

  /**
   * What the chunk's features say about the ground under an item: whether it is worked, and how glacial.
   *
   * Both read off the chunk's own feature query, which every chunk that could own a given item ran with the
   * same margin, so the verdict is a pure function of the item's position.
   */
  private class Ground(nearby: List<VectorFeature>) {

    private val worked: List<VectorFeature> = nearby.filter { it.kind in WORKED }
    private val glacial: List<PolylineFeature> = nearby
      .filter { it.kind == FeatureKind.MORAINE || it.kind == FeatureKind.GLACIAL_TROUGH }
      .filterIsInstance<PolylineFeature>()

    fun isWorked(x: Double, y: Double): Boolean = worked.any { feature ->
      when (feature) {
        is AreaFeature -> feature.contains(x, y)
        is PointFeature -> {
          val dx = x - feature.center.x
          val dy = y - feature.center.y
          dx * dx + dy * dy <= feature.radius * feature.radius
        }
        else -> false
      }
    }

    fun glacialAt(x: Double, y: Double, reach: Double): Double {
      if (glacial.isEmpty() || reach <= 0.0) return 0.0
      var best = 0.0
      for (line in glacial) {
        if (!line.bbox.expanded(reach).contains(x, y)) continue
        val d = line.centerline.project(Vec2d(x, y)).distance
        if (d < reach) best = maxOf(best, 1.0 - d / reach)
      }
      return best
    }

    private companion object {
      val WORKED = setOf(FeatureKind.FIELD, FeatureKind.SETTLEMENT_GRADING, FeatureKind.CEMETERY)
    }
  }

  companion object {
    /** The kinds this emits, for a consumer counting or filtering them. */
    val KINDS = setOf(PropKind.BOULDER, PropKind.LOOSE_STONE, PropKind.STICK, PropKind.RESIN)

    /** The kinds that follow the trees, for which how glacial the ground is decides nothing. */
    private val UNDER_TREES = setOf(PropKind.STICK, PropKind.RESIN)

    private const val BOULDER_SALT = 0x426F756C646572L
    private const val STONE_SALT = 0x53746F6E6573L
    private const val STICK_SALT = 0x537469636B73L
    private const val RESIN_SALT = 0x526573696EL
    private const val PATCH_SALT = 0x4C6F6F7450L
    private const val PATCH_OCTAVES = 2
  }
}
