package net.bestia.worldgen.voxel

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.fields.Noise
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Quantize
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.sqrt

/** Tuning for [VegetationScatter]. */
data class VegetationParams(

  /**
   * Edge of the lattice square that holds at most one tree, in metres.
   *
   * The **decision unit**, and the whole reason the scatter does not come out as a checkerboard - see the
   * class KDoc. It is also the hard floor on stem spacing: at four metres the densest possible wood is one
   * stem per sixteen square metres, or 625 to the hectare, which is a closed mature forest. Halving it
   * quadruples the tree count and the cost of a forest chunk with it.
   */
  val cellSize: Double = 4.0,

  /**
   * How much of its cell a trunk may wander from the centre, as a share of [cellSize].
   *
   * Zero would put every trunk on a visible four-metre grid. One would let two trunks in adjacent cells
   * land on the same spot. The reach this adds is what sets [VegetationScatter.searchCells] and the chunk
   * halo, so it is not free.
   */
  val jitterShare: Double = 0.7,

  /** Wavelength of the patch field in metres: how far it is across a wood, near enough. */
  val patchWavelength: Double = 140.0,

  /**
   * Patch value below which nothing grows at all.
   *
   * What makes a forest have clearings in it rather than thinning smoothly to nothing everywhere. The field
   * is uniform-ish over `[0,1]`, so this is roughly the share of otherwise-wooded ground that comes out open.
   */
  val clearingCutoff: Double = 0.40,

  /** Width of the ramp above [clearingCutoff], so a clearing has an edge rather than a wall. */
  val patchSoftness: Double = 0.22,

  /** Soil depth in metres at which the soil term stops limiting anything. */
  val soilDepthFull: Double = 0.9,

  /**
   * The one gain on [net.bestia.worldgen.bio.Biome.canopy], and the knob to turn when a world is too bare
   * or too thick.
   *
   * Everything else in the density is a claim about the world - which biome, how deep the soil, whether this
   * is a clearing - so putting the single free scalar here rather than distributing it over three of them
   * keeps those three falsifiable.
   */
  val canopyGain: Double = 1.0,

  /**
   * Cap on the per-cell tree probability.
   *
   * Also load bearing as an *optimisation*: the cheap reject in [VegetationScatter.treeAt] tests one hash
   * against this before evaluating the density field, which is only sound because nothing may exceed it.
   */
  val maxDensity: Double = 0.9,

  /** Clear bole plus crown centre, in metres. The crown is centred on the top of the trunk. */
  val minTrunkHeight: Double = 4.5,
  val maxTrunkHeight: Double = 12.0,

  val minCanopyRadius: Double = 1.7,
  val maxCanopyRadius: Double = 3.2,

  /** Crown height as a multiple of its radius. Above one is a tall crown, below one a spreading crown. */
  val crownAspect: Double = 1.15
) : Params {

  init {
    require(cellSize > 0.0) { "cellSize must be positive, was $cellSize" }
    require(jitterShare in 0.0..1.0) { "jitterShare must be in [0,1], was $jitterShare" }
    require(patchWavelength > 0.0) { "patchWavelength must be positive, was $patchWavelength" }
    require(clearingCutoff in 0.0..1.0) { "clearingCutoff must be in [0,1], was $clearingCutoff" }
    require(patchSoftness > 0.0) { "patchSoftness must be positive, was $patchSoftness" }
    require(soilDepthFull > 0.0) { "soilDepthFull must be positive, was $soilDepthFull" }
    require(canopyGain > 0.0) { "canopyGain must be positive, was $canopyGain" }
    require(maxDensity > 0.0 && maxDensity <= 1.0) { "maxDensity must be in (0,1], was $maxDensity" }
    require(minTrunkHeight > 0.0) { "minTrunkHeight must be positive, was $minTrunkHeight" }
    require(maxTrunkHeight >= minTrunkHeight) {
      "maxTrunkHeight $maxTrunkHeight is below minTrunkHeight $minTrunkHeight"
    }
    require(minCanopyRadius > 0.0) { "minCanopyRadius must be positive, was $minCanopyRadius" }
    require(maxCanopyRadius >= minCanopyRadius) {
      "maxCanopyRadius $maxCanopyRadius is below minCanopyRadius $minCanopyRadius"
    }
    require(crownAspect > 0.0) { "crownAspect must be positive, was $crownAspect" }
  }

  override fun digest() = ParamsDigest()
    .put("cellSize", cellSize)
    .put("jitterShare", jitterShare)
    .put("patchWavelength", patchWavelength)
    .put("clearingCutoff", clearingCutoff)
    .put("patchSoftness", patchSoftness)
    .put("soilDepthFull", soilDepthFull)
    .put("canopyGain", canopyGain)
    .put("maxDensity", maxDensity)
    .put("minTrunkHeight", minTrunkHeight)
    .put("maxTrunkHeight", maxTrunkHeight)
    .put("minCanopyRadius", minCanopyRadius)
    .put("maxCanopyRadius", maxCanopyRadius)
    .put("crownAspect", crownAspect)
}

/**
 * Where the trees are.
 *
 * A world of five hundred kilometres holds on the order of a billion trees, so they are **implicit**: there
 * is no feature, no marker and no raster of them anywhere, only a function from a world position to whether
 * something grows there. `PointMarker`'s KDoc makes the same argument one order of magnitude down for ore
 * deposits, and a tree is three orders smaller than a deposit.
 *
 * ### The checkerboard, and the three things it takes to avoid one
 *
 * `SurfaceSampler.biomeAt` paid for this lesson once already and the note there is worth re-reading: a
 * per-position hash at a metre per voxel reads as *display noise*, not as ground, because each voxel's
 * decision is independent of its neighbours'. Smoothing the *probability* does not fix it - a smooth field
 * sampled per column is still an independent coin flip per column, and a 50% probability still draws a 50/50
 * checkerboard. Vegetation needs all three of:
 *
 * 1. a **smooth field**, so density varies as a landscape does rather than as noise ([patchAt]);
 * 2. a **cutoff** in it, so a wood has an edge and a clearing is a place rather than a thin patch;
 * 3. a **decision unit larger than a voxel** - this lattice. One tree per four-metre cell, and a column
 *    asks its neighbouring cells whether any canopy reaches it.
 *
 * The third is the one that cannot be skipped, and it is why the architecture document's standing permission
 * that "chunk-seeded randomness is fine for vegetation" is still unused here. A four-metre canopy is not one
 * column; a chunk-seeded tree at a border is half a tree.
 *
 * ### Why two chunks agree
 *
 * The lattice index is **integer arithmetic on a quantised world coordinate** - [Quantize.toFixed] then
 * `floorDiv` - so it is a pure function of position with no floating-point boundary to land either side of.
 * Everything a tree is follows from that index by hashing, and everything a *column* does follows from the
 * tree. The two remaining floating-point decisions, "is this column under that crown" and "is this the
 * trunk's own column", go through [Quantize] and through the same integer column index respectively.
 *
 * The one input that is not a function of position is the **ground under a trunk**, and it is handled by
 * asking for it once, at the trunk, through the same [net.bestia.worldgen.core.ColumnHeights] both chunks
 * build - see [TrunkSite] and [halo]. Deriving it per column instead would shear every crown across a slope
 * and would differ either side of a border.
 *
 * ### Not tuned by argument
 *
 * Densities here are measurements. `probe -Psurvey` reports the wooded share and the stems per hectare, and
 * the numbers below are what produced the figures recorded in [VegetationParams]. Phase 7's droplet density
 * was set twenty times too high by a plausible-sounding argument, which is the standing reason not to.
 */
class VegetationScatter(
  private val config: WorldConfig,

  /**
   * The surface classifier, shared with the materialiser rather than rebuilt.
   *
   * Same argument `Stratigraphy.of` makes about rock: `VegetationStage` rasterises the canopy from this
   * function and the chunk tier plants trees from it, and the two agree only if it is literally one object
   * built from one set of layers. See [SurfaceSampler.of].
   */
  private val surface: SurfaceSampler,
  seed: Long,
  val params: VegetationParams = VegetationParams()
) {

  private val treeSeed = GenRng.mix64(seed xor TREE_SALT)
  private val patchSeed = GenRng.mix64(seed xor PATCH_SALT)

  /** Fixed-point cell edge, so the lattice index is integer division and not a rounded quotient. */
  private val cellUnits = Quantize.toFixed(params.cellSize)

  private val voxelUnits = Quantize.toFixed(config.voxelSize)

  private val jitter = params.cellSize * params.jitterShare

  /** How far a crown reaches from its own trunk, in metres. */
  val crownReach = params.maxCanopyRadius

  /**
   * How many cells either side of a column's own must be examined.
   *
   * Derived rather than written down as 1, because it is a *consequence* of three tunables and an
   * under-count is a crown with a straight edge cut through it - the same defect `CaveNetwork.reach` and
   * `TownStructures.MAX_WALL_HALF_THICKNESS` exist to prevent, and equally invisible in a unit test.
   *
   * A tree in the cell `n` away has its trunk at least `n * cellSize - cellSize / 2 - jitter / 2` from this
   * column, so cell `n` can reach it only while that is under [crownReach]. Solving for `n` and taking the
   * last integer strictly below it is the expression here.
   */
  val searchCells: Int = ceil((crownReach + jitter * 0.5) / params.cellSize + 0.5).toInt() - 1

  /**
   * Columns of a neighbouring chunk whose ground a tree in this one may need.
   *
   * A trunk up to [crownReach] metres outside the chunk can drop a crown inside it, and the crown's
   * elevation is measured from the ground at the *trunk*. That ground has to come from the same height
   * pipeline as everything else or the two tiers disagree, so it comes out of a halo on
   * `ChunkColumnSource.heights`. [candidatesIn] discards any trunk beyond it, which is what keeps this
   * bound and that filter the same statement rather than two.
   */
  val halo: Int = ceil(crownReach / config.voxelSize).toInt()

  /** Mean crown area over one cell's area: the density-to-cover conversion [coverAt] applies. */
  private val crownAreaShare: Double = run {
    val meanRadius = (params.minCanopyRadius + params.maxCanopyRadius) * 0.5
    PI * meanRadius * meanRadius / (params.cellSize * params.cellSize)
  }

  /**
   * The chance that a four-metre cell at this position holds a tree, in `[0, params.maxDensity]`.
   *
   * Three terms, and each is a claim that can be checked against a map.
   * [canopy][net.bestia.worldgen.bio.Biome.canopy] is the per-biome term; soil depth is the one *local*
   * signal independent of the biome and already in frame; and the patch field is what makes a wood a wood
   * rather than an even stipple over a whole climate zone.
   *
   * **Not `Biome.litter`**, though that is the scalar the biome table already had and the obvious thing to
   * reuse. Litter is a *fertility* input - `SOIL_FERTILITY` is `0.20 * litter + ...` - and grassland is one
   * of the best litter producers on the list while being almost treeless. Reusing it would have put a wood
   * on every prairie at four fifths the density of a temperate forest.
   *
   * Deliberately **not** consulted either: `SOIL_FERTILITY` and `PRECIPITATION`. Both would need a new
   * constructor argument, and both partly re-count what the biome term already says - the classifier chose
   * that biome *from* the rainfall - so folding one in would square the same evidence and call it two.
   */
  fun densityAt(worldX: Double, worldY: Double): Double =
    (siteDensityAt(worldX, worldY) * patchAt(worldX, worldY)).coerceAtMost(params.maxDensity)

  /**
   * Everything in the density that is a property of the *place* rather than of the patch: biome and soil.
   *
   * Split out so that `VegetationStage` can hold it constant across a kilometre cell and sub-sample only
   * [patchAt], which is the sole term that varies inside one. That is not an approximation for convenience -
   * both of these are read from kilometre rasters, so a cell has one value of each by construction, while the
   * patch field goes through seven full cycles across the same cell and is the entire source of the variance.
   * Averaging all three together instead needs an order of magnitude more samples to reach the same precision,
   * and at sixteen samples it printed a canopy map that was visibly grainy at the cell grid - a picture of the
   * sampling rather than of the world.
   */
  fun siteDensityAt(worldX: Double, worldY: Double): Double {
    val canopy = surface.biomeAt(worldX, worldY).canopy
    if (canopy <= 0.0) return 0.0

    val soil = PolylineFeature.smoothstep(surface.soilDepthAt(worldX, worldY) / params.soilDepthFull)
    if (soil <= 0.0) return 0.0

    return canopy * params.canopyGain * soil
  }

  /**
   * The smooth field that decides where a wood is, in `[0,1]`, and zero in a clearing.
   *
   * An order of magnitude coarser than `SurfaceSampler.PATCH_WAVELENGTH`, for a reason rather than by
   * accident: that field was tuned for a patch of heath in grassland, which is metres across. A wood is sixty
   * to four hundred metres across, and at fourteen metres this would mottle a forest rather than bound it.
   */
  fun patchAt(worldX: Double, worldY: Double): Double {
    val raw = (Noise.fbm(
      patchSeed, worldX / params.patchWavelength, worldY / params.patchWavelength, PATCH_OCTAVES
    ) + 1.0) * 0.5
    return PolylineFeature.smoothstep((raw - params.clearingCutoff) / params.patchSoftness)
  }

  /**
   * The share of ground under a crown at a given per-cell tree probability, in `[0,1]`.
   *
   * `1 - exp(-density * crownArea/cellArea)` rather than the naive product, because **crowns overlap**. A
   * mean crown is a fifth larger than a cell, so the product passes 1.0 while a fifth of the ground is still
   * open, and a layer that saturates early cannot rank a dense wood against a very dense one - which ranking
   * is all any consumer of it does. This is the void fraction of a Poisson field of discs; the lattice is not
   * quite Poisson, holding at most one tree per cell, so it is a slight under-estimate at high density and
   * exact at low.
   */
  fun coverOf(density: Double): Double = 1.0 - exp(-density * crownAreaShare)

  fun coverAt(worldX: Double, worldY: Double): Double = coverOf(densityAt(worldX, worldY))

  /**
   * The trees whose crowns can reach a chunk, before anything asks how high the ground is under them.
   *
   * Split from [plant] purely for cost, and the saving is worth the seam in the API: resolving the ground
   * needs the column heights of a chunk *plus a halo*, which is seventy per cent more heightfield
   * evaluations than the chunk itself needs. Most of the world is ocean, desert, ice or grass, and this pass
   * - one hash per cell, and the density field only for the cells that survive it - answers "no trees here"
   * for a hundred cells rather than for eighteen hundred columns.
   */
  fun candidatesIn(chunk: ChunkPos): Candidates {
    val bounds = config.chunkBounds(chunk)
    val fromX = cellOf(bounds.minX) - searchCells
    val untilX = cellOf(bounds.maxX) + searchCells + 1
    val fromY = cellOf(bounds.minY) - searchCells
    val untilY = cellOf(bounds.maxY) + searchCells + 1

    val cellsX = (untilX - fromX).toInt()
    val cellsY = (untilY - fromY).toInt()
    val candidates = Candidates(fromX, fromY, cellsX, cellsY)

    // A cell overlapping the chunk can still hold a trunk whose crown falls entirely outside it, and such a
    // tree is not merely wasted work - its ground would be a column further out than [halo] reaches. Both
    // facts come off the same bound, so they are one line rather than two constants that have to agree.
    val reachable = bounds.expanded(crownReach)

    for (cellY in fromY until untilY) {
      for (cellX in fromX until untilX) {
        val tree = treeAt(cellX, cellY) ?: continue
        if (!reachable.contains(tree.x, tree.y)) continue
        candidates.put(cellX, cellY, tree)
      }
    }

    return candidates
  }

  /**
   * Resolves the ground under each candidate and drops the ones that may not stand.
   *
   * The vetoes are split by who can answer them, and the split is not arbitrary: everything decided here is
   * a **pure function of the trunk's position**, which is what lets a neighbouring chunk reach the same
   * verdict about the same tree without seeing this chunk's voxels. [TrunkSite] carries the rest.
   */
  fun plant(candidates: Candidates, site: TrunkSite): TreeLattice {
    val lattice = TreeLattice(candidates, params, cellUnits, voxelUnits, searchCells)

    for (i in candidates.indices) {
      val x = candidates.trunkX[i]
      if (x.isNaN()) continue
      val y = candidates.trunkY[i]

      val ground = site.groundAt(x, y)
      if (ground.isNaN()) continue

      // Nothing grows out of standing water. The biome term already refuses OCEAN and LAKE, but a biome is a
      // kilometre cell and a pond edge is not, so the water surface itself has the last word.
      if (surface.waterLevelAt(x, y) > ground) continue

      // Resolved here, at the trunk, and carried on the lattice - see TreeLattice.blighted for why it may
      // not be asked again per column.
      val isBlighted = surface.isBlightedAt(x, y)

      // Nor out of ice or year-round snow. Asked of the *cap block* rather than of the temperature, so that
      // the one place deciding what the top of a column is made of is also the place deciding whether
      // anything can root in it. Blighted ground is neither, so a corrupted wood keeps its trees.
      val cap = SurfaceCover.cap(surface.biomeAt(x, y), surface.temperatureAt(x, y), 0.0, isBlighted)
      if (cap == BlockType.ICE || cap == BlockType.SNOW) continue

      lattice.plant(i, ground, isBlighted)
    }

    return lattice
  }

  /**
   * The tree in one lattice cell, or null for an empty cell.
   *
   * One hash per cell, walked with [GenRng.mix64] for the three further draws rather than re-hashing the
   * key: four `hashUnit` calls would be four vararg arrays per cell, and a forest chunk visits a hundred
   * cells while a *world* visits rather more.
   *
   * Trunk height and crown radius come off the **same** draw, so a big tree has a big crown. Two draws would
   * produce saplings with the canopy of an oak.
   */
  private fun treeAt(cellX: Long, cellY: Long): Tree? {
    val key = GenRng.hash(treeSeed, cellX, cellY)
    val roll = GenRng.unit(key)
    // Cheap reject before the density field, sound only because densityAt is capped at the same value.
    if (roll >= params.maxDensity) return null

    val jitterX = (GenRng.unit(GenRng.mix64(key + 1)) - 0.5) * jitter
    val jitterY = (GenRng.unit(GenRng.mix64(key + 2)) - 0.5) * jitter
    val x = (cellX + 0.5) * params.cellSize + jitterX
    val y = (cellY + 0.5) * params.cellSize + jitterY

    // Judged where the trunk stands rather than at the cell centre: a tree jittered onto a river bank is on
    // the river bank.
    if (roll >= densityAt(x, y)) return null

    val size = GenRng.unit(GenRng.mix64(key + 3))
    return Tree(
      x = x,
      y = y,
      trunkHeight = params.minTrunkHeight + (params.maxTrunkHeight - params.minTrunkHeight) * size,
      canopyRadius = params.minCanopyRadius + (params.maxCanopyRadius - params.minCanopyRadius) * size
    )
  }

  /** Lattice cell index of a world coordinate. Integer division of a fixed-point value; see the class KDoc. */
  private fun cellOf(world: Double): Long = Math.floorDiv(Quantize.toFixed(world), cellUnits)

  /** One tree, as the lattice draws it before the ground is known. */
  internal class Tree(val x: Double, val y: Double, val trunkHeight: Double, val canopyRadius: Double)

  /**
   * Everything about a trunk's surroundings that the scatter cannot see for itself.
   *
   * The scatter knows the climate, the soil and the water; it does not know that somebody paved this spot,
   * built a granary on it, or opened a cave mouth under it. Those are questions about the *other* producers
   * in the chunk tier, and they are asked at the trunk position - never at the column being filled - so that
   * the neighbouring chunk drawing the other half of the same crown reaches the same verdict.
   */
  fun interface TrunkSite {
    /** Ground elevation at a trunk, or [Double.NaN] when nothing may be planted there. */
    fun groundAt(worldX: Double, worldY: Double): Double
  }

  /**
   * The trees over a chunk's cell range, before [plant] resolves their ground.
   *
   * A struct of arrays rather than a list of objects, because it is rebuilt for every chunk of every world
   * and the whole point of the split is that it must be cheap when the answer is "none".
   */
  class Candidates internal constructor(
    internal val fromCellX: Long,
    internal val fromCellY: Long,
    internal val cellsX: Int,
    internal val cellsY: Int
  ) {
    internal val trunkX = DoubleArray(cellsX * cellsY) { Double.NaN }
    internal val trunkY = DoubleArray(cellsX * cellsY)
    internal val trunkHeight = DoubleArray(cellsX * cellsY)
    internal val canopyRadius = DoubleArray(cellsX * cellsY)

    var count: Int = 0
      private set

    val isEmpty get() = count == 0

    internal val indices get() = 0 until cellsX * cellsY

    internal fun indexOf(cellX: Long, cellY: Long): Int {
      val x = (cellX - fromCellX).toInt()
      val y = (cellY - fromCellY).toInt()
      if (x < 0 || y < 0 || x >= cellsX || y >= cellsY) return -1
      return y * cellsX + x
    }

    internal fun put(cellX: Long, cellY: Long, tree: Tree) {
      val i = indexOf(cellX, cellY)
      trunkX[i] = tree.x
      trunkY[i] = tree.y
      trunkHeight[i] = tree.trunkHeight
      canopyRadius[i] = tree.canopyRadius
      count++
    }
  }

  private companion object {
    const val TREE_SALT = 0x7A31B0DE4C0F55L
    const val PATCH_SALT = 0x2E9C64B7D1A308L

    /** Two octaves: enough that a wood has an irregular edge, few enough that it stays one wood. */
    const val PATCH_OCTAVES = 2
  }
}

/**
 * The trees reaching one chunk, with the ground under each already resolved.
 *
 * Built once per chunk and read once per column, which is what makes the per-column cost a handful of array
 * reads: a four-metre cell covers sixteen columns and each column consults nine cells, so a cell that were
 * recomputed on demand would be recomputed a hundred and forty-four times.
 */
class TreeLattice internal constructor(
  private val candidates: VegetationScatter.Candidates,
  private val params: VegetationParams,
  private val cellUnits: Long,
  private val voxelUnits: Long,
  /** Handed down rather than recomputed: two derivations of a reach are two chances to get one wrong. */
  private val searchCells: Int
) {

  private val base = DoubleArray(candidates.trunkX.size) { Double.NaN }
  private val trunkColumnX = LongArray(candidates.trunkX.size)
  private val trunkColumnY = LongArray(candidates.trunkX.size)

  /**
   * Whether each tree is blighted, resolved **at its trunk** and carried here.
   *
   * The same rule as the crown hanging from the ground under its own trunk, and it fails the same way if
   * broken. A crown spans columns and reaches into the neighbouring chunk; if each chunk asked
   * `isBlightedAt` about the column it happens to be drawing, a tree standing on a corruption fringe would
   * come out blighted in one chunk and green in the other, with a seam straight down the middle of it.
   * Deciding once at the trunk makes both chunks reach the same verdict about the same tree with no
   * communication - which is the whole property this scatter exists to have.
   */
  private val blighted = BooleanArray(candidates.trunkX.size)

  var count: Int = 0
    private set

  val isEmpty get() = count == 0

  internal fun plant(index: Int, ground: Double, isBlighted: Boolean) {
    base[index] = ground
    blighted[index] = isBlighted
    trunkColumnX[index] = Math.floorDiv(Quantize.toFixed(candidates.trunkX[index]), voxelUnits)
    trunkColumnY[index] = Math.floorDiv(Quantize.toFixed(candidates.trunkY[index]), voxelUnits)
    count++
  }

  /**
   * Adds a span of foliage for every tree standing over this column.
   *
   * ### The crown is an ellipsoid and the trunk is one column
   *
   * A crown is drawn the way a cave passage is - `sqrt(1 - t^2)` across its width, so it meets the trunk at
   * full height and tapers to nothing at its edge - which costs one square root and is the difference
   * between a tree and a green cylinder. The trunk goes into exactly **one** column, chosen by integer
   * column index rather than by a distance test, so a trunk near a column boundary cannot come out as two
   * trunks or none.
   *
   * ### Foliage first, then the trunk
   *
   * They are written in that order because the writer only fills air: the crown claims the top of the trunk,
   * and the trunk then fills the clear bole beneath it. Reversed, a tree would be a bare pole with a
   * hemisphere balanced on it.
   */
  fun columnAt(worldX: Double, worldY: Double, into: StructureSpans) {
    if (count == 0) return

    val cellX = Math.floorDiv(Quantize.toFixed(worldX), cellUnits)
    val cellY = Math.floorDiv(Quantize.toFixed(worldY), cellUnits)
    val columnX = Math.floorDiv(Quantize.toFixed(worldX), voxelUnits)
    val columnY = Math.floorDiv(Quantize.toFixed(worldY), voxelUnits)

    for (dy in -searchCells..searchCells) {
      for (dx in -searchCells..searchCells) {
        val i = candidates.indexOf(cellX + dx, cellY + dy)
        if (i < 0) continue
        if (base[i].isNaN()) continue

        val offsetX = worldX - candidates.trunkX[i]
        val offsetY = worldY - candidates.trunkY[i]
        val distance = sqrt(offsetX * offsetX + offsetY * offsetY)
        val radius = candidates.canopyRadius[i]
        val crownCentre = base[i] + candidates.trunkHeight[i]

        // Quantised, so two chunks cannot decide the edge of a crown differently - the same branch
        // `CaveNetwork` and the doorway test in `TownStructures` put through Quantize.
        if (!Quantize.isAbove(distance, radius)) {
          val t = (distance / radius).coerceIn(0.0, 1.0)
          val half = radius * params.crownAspect * sqrt((1.0 - t * t).coerceAtLeast(0.0))
          into.add(
            crownCentre - half,
            crownCentre + half,
            if (blighted[i]) BlockType.BLIGHTED_LEAVES else BlockType.LEAVES
          )
        }

        if (columnX == trunkColumnX[i] && columnY == trunkColumnY[i]) {
          into.add(base[i], crownCentre, if (blighted[i]) BlockType.BLIGHTED_LOG else BlockType.LOG)
        }
      }
    }
  }
}
