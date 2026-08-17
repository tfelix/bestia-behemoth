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
import kotlin.math.ln
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
  val crownAspect: Double = 1.15,

  /**
   * Share of the simulated lattice that becomes an *entity*, in `(0, 1]`.
   *
   * The entity budget, and the only knob for it. [cellSize] stays the simulation's decision unit and
   * the hard floor on stem spacing; this decides how many of those stems a runtime is asked to
   * materialise. One in four at the default, which is a mean spacing around eight metres against the
   * four-metre floor.
   *
   * Continuous rather than an integer block factor on purpose. Choosing one cell per `k x k` block
   * quantises the achievable spacing to multiples of [cellSize] - four, eight, twelve - and cannot
   * express six at all; worse, it guarantees exactly one tree per block, so a wood comes out with no
   * clumps and no gap wider than one block. That reads as an orchard. See [VegetationScatter.clumpAt]
   * for the other half of the argument.
   *
   * The emitted set is a subset of the simulated one at **every** value, which is what keeps
   * `LayerId.CANOPY_COVER` and a stand's advertised capacity two views of one function rather than two
   * models of one thing.
   *
   * ### The mean retention is this number only while `entityShare * clumpAt <= 1`
   *
   * [VegetationScatter.clumpAt] has a mean of one and a range of roughly `[0,2]`, and the retention
   * threshold is clamped so it can never exceed the simulated density. So the expected share retained is
   * exactly this value while the product stays under one - true at the default, where it peaks around a
   * half - and falls below it once the clamp starts biting, which it does over about half the world at a
   * share of one. A stand's advertised capacity is computed from this number, so **a share much above a
   * half makes that advertisement optimistic** rather than wrong in an interesting way.
   *
   * A share of one therefore means "as many as the clumping allows", not literally every simulated tree.
   */
  val entityShare: Double = 0.25,

  /**
   * Wavelength in metres of the field that makes entity trees clump.
   *
   * Thirty metres: the scale a player walks through, so a wood has thickets and glades in it rather
   * than an even pitch. Deliberately a **separate field** from [patchWavelength] rather than another
   * octave on it - [patchWavelength] is 140 m with a hard [clearingCutoff] and feeds
   * [VegetationScatter.coverAt] and therefore `CANOPY_COVER`, so adding detail there would move the
   * canopy raster, the TIMBER resource and the biome-agreement invariant. This one gates entity
   * retention only and never reaches `VegetationStage`.
   */
  val clumpWavelength: Double = 30.0
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
    require(entityShare > 0.0 && entityShare <= 1.0) { "entityShare must be in (0,1], was $entityShare" }
    require(clumpWavelength > 0.0) { "clumpWavelength must be positive, was $clumpWavelength" }
  }

  /**
   * Mean crown area over one cell's area: the density-to-cover conversion.
   *
   * Here rather than in [VegetationScatter] because two readers need it and they are in different tiers -
   * the scatter converts density to cover, and `VegetationStandStage` converts back the other way to work
   * out how many entities a disc of ground holds. One definition, or the world tier advertises a capacity
   * computed from a slightly different crown than the chunk tier plants.
   */
  val crownAreaShare: Double
    get() {
      val meanRadius = (minCanopyRadius + maxCanopyRadius) * 0.5
      return PI * meanRadius * meanRadius / (cellSize * cellSize)
    }

  /**
   * Per-cell tree probability that would produce a given canopy cover: the inverse of
   * [VegetationScatter.coverOf].
   *
   * `LayerId.CANOPY_COVER` is a *void fraction* - how shaded this ground is - and nothing can read a tree
   * count off it directly, which is why a stand's capacity cannot simply be its cover times its area. This
   * is the one honest bridge between the two, and it exists because `coverOf` happens to be invertible.
   *
   * Clamped at [maxDensity], which the forward function is also capped at, so a cover of one - unreachable
   * from the forward direction, since `coverOf(maxDensity)` is about 0.65 at the defaults - does not come
   * back as an infinity.
   */
  fun densityOf(cover: Double): Double {
    if (cover <= 0.0) return 0.0
    return (-ln(1.0 - cover.coerceAtMost(0.999)) / crownAreaShare).coerceAtMost(maxDensity)
  }

  /**
   * Expected *entity* props per square metre of ground at a given canopy cover.
   *
   * What a `VEGETATION_STAND` advertises, and what an invariant compares against a count of the props
   * actually emitted over the same ground. Accurate while `entityShare * clumpAt <= 1` - see [entityShare].
   */
  fun entitiesPerSquareMetre(cover: Double): Double =
    densityOf(cover) * entityShare / (cellSize * cellSize)

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
    .put("entityShare", entityShare)
    .put("clumpWavelength", clumpWavelength)
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
 * Densities here are measurements. `probe -Psurvey` reports the stems per hectare and `probe --props` prints
 * the props of one chunk, and the numbers below are what produced the figures recorded in [VegetationParams].
 * Phase 7's droplet density was set twenty times too high by a plausible-sounding argument, which is the
 * standing reason not to.
 *
 * That claim was false for as long as it stood here - `-Psurvey` printed a biome breakdown and no stem count
 * at all, so the one number this paragraph points at could not be read off the tool it points at. Both prints
 * exist now. Measured on Genesis: 24 tree props per hectare averaged over all land, 17 to 50 inside a wood.
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
  private val clumpSeed = GenRng.mix64(seed xor CLUMP_SALT)

  /** Fixed-point cell edge, so the lattice index is integer division and not a rounded quotient. */
  private val cellUnits = Quantize.toFixed(params.cellSize)

  private val voxelUnits = Quantize.toFixed(config.voxelSize)

  private val jitter = params.cellSize * params.jitterShare

  /** Mean crown area over one cell's area: the density-to-cover conversion [coverAt] applies. */
  private val crownAreaShare: Double = params.crownAreaShare

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
   * A mean-one multiplier on entity retention, so entity trees clump instead of coming out on a pitch.
   *
   * ### Why the mean has to be one
   *
   * Not cosmetic. It is what keeps the expected entity count equal to
   * `cover x area x entityShare / cellArea` with no clump term in it, so a `VEGETATION_STAND` can
   * advertise a capacity the chunk tier will actually fill and an invariant can compare the two. A
   * field with a mean of 1.2 would make every stand in the world under-promise by a fifth, and nothing
   * would notice because both sides would still look plausible.
   *
   * `Noise.fbm` is normalised by the sum of its amplitudes and `gradient2d` is scaled to roughly
   * `[-1,1]` - the same assumption [patchAt] already makes when it maps `fbm` onto `[0,1]` - so
   * `1 + fbm` has a mean of one by the symmetry of gradient noise rather than by a fitted constant.
   * `VegetationPropsTest` measures it rather than trusting that.
   *
   * The floor at zero is a guard, not a mechanism: it can only bite where `fbm` undershoots -1, and a
   * negative multiplier would merely reject the cell anyway.
   */
  fun clumpAt(worldX: Double, worldY: Double): Double =
    (1.0 + Noise.fbm(
      clumpSeed, worldX / params.clumpWavelength, worldY / params.clumpWavelength, CLUMP_OCTAVES
    )).coerceAtLeast(0.0)

  /**
   * The tree in one lattice cell, or null for an empty cell.
   *
   * One hash per cell, walked with [GenRng.mix64] for the three further draws rather than re-hashing the
   * key: four `hashUnit` calls would be four vararg arrays per cell, and a forest chunk visits a hundred
   * cells while a *world* visits rather more.
   *
   * Trunk height and crown radius come off the **same** draw, so a big tree has a big crown. Two draws would
   * produce saplings with the canopy of an oak.
   *
   * ### The entity lattice is the same lattice, thinned
   *
   * The thinning compares the *same* `roll` against a lower threshold, which is what makes the emitted set a
   * **strict subset** of the simulated one rather than a second scatter that happens to look similar. Reusing
   * the roll costs nothing and biases nothing: jitter and size come off further mixes of the key, so which
   * trees survive is independent of what they are.
   *
   * The `coerceAtMost(density)` is what makes the subset property hold for *any* parameters rather than only
   * for sane ones - `entityShare * clumpAt` exceeding one would otherwise retain a cell the simulation
   * rejected. It cannot bite at the defaults, where the product peaks around a half.
   *
   * This briefly took a `thinToEntities` flag, while the voxel path still wanted the unthinned set. There is
   * one path now.
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
    val density = densityAt(x, y)
    val threshold = (density * params.entityShare * clumpAt(x, y)).coerceAtMost(density)
    if (roll >= threshold) return null

    val size = GenRng.unit(GenRng.mix64(key + 3))
    return Tree(
      x = x,
      y = y,
      trunkHeight = params.minTrunkHeight + (params.maxTrunkHeight - params.minTrunkHeight) * size,
      canopyRadius = params.minCanopyRadius + (params.maxCanopyRadius - params.minCanopyRadius) * size
    )
  }

  /**
   * The trees whose own trunk stands inside this chunk, as props for a runtime to make entities of.
   *
   * ### One pass, no halo
   *
   * This used to be two - a cheap `candidatesIn` hash pass and a `plant` pass that resolved the ground - and
   * the split cost a halo of ground columns in the chunks around this one. Both existed for one reason: a
   * crown spans columns and reaches into the neighbouring chunk, so a chunk drawing *voxels* had to know about
   * trees standing outside it, and had to agree with its neighbour about the ground under each.
   *
   * **A prop is a point.** Nothing outside this chunk can contribute one, so the crown reach, the cell search
   * radius and the halo are all gone, and this needs the chunk's own columns and nothing more - about seventy
   * per cent fewer heightfield evaluations on a forested chunk than the voxel path cost.
   *
   * ### Ownership is an integer test
   *
   * A prop belongs to the chunk containing its trunk's **voxel column**. Deliberately not `Aabb.contains`:
   * `vector/Aabb` is a closed interval, so a trunk exactly on a chunk boundary would be claimed by both of the
   * chunks that share it and appear twice in the world.
   *
   * The cell range is the cells overlapping the chunk with no expansion, which is sound because jitter
   * cannot carry a trunk out of its own cell - the offset is at most `cellSize * jitterShare / 2` and
   * `jitterShare` is required to be at most one.
   *
   * Rows are walked in order, so the result does not depend on thread scheduling.
   *
   * @param site ground elevation, and `NaN` where the other producers in the chunk tier have already
   *   claimed the spot. The same set of questions [TrunkSite] answers for the voxel path, plus the ones
   *   only an entity needs - see `ChunkMaterializer.propSite`.
   */
  fun propsIn(chunk: ChunkPos, site: PropSite, into: PropInstances) {
    val bounds = config.chunkBounds(chunk)
    val fromX = cellOf(bounds.minX)
    val untilX = cellOf(bounds.maxX) + 1
    val fromY = cellOf(bounds.minY)
    val untilY = cellOf(bounds.maxY) + 1

    val chunkSize = config.chunkSize.toLong()

    for (cellY in fromY until untilY) {
      for (cellX in fromX until untilX) {
        val tree = treeAt(cellX, cellY) ?: continue

        if (config.chunkOf(tree.x) != chunk.x) continue
        if (config.chunkOf(tree.y) != chunk.y) continue

        val ground = site.groundAt(tree.x, tree.y)
        if (ground.isNaN()) continue

        // Nothing grows out of standing water. The biome term already refuses OCEAN and LAKE, but a
        // biome is a kilometre cell and a pond edge is not, so the water surface has the last word.
        if (surface.waterLevelAt(tree.x, tree.y) > ground) continue

        val isBlighted = surface.isBlightedAt(tree.x, tree.y)

        // Nor out of ice or year-round snow. Asked of the cap block rather than of the temperature, so
        // the one place deciding what the top of a column is made of also decides whether anything can
        // root in it. Blighted ground is neither, so a corrupted wood keeps its trees.
        val cap = SurfaceCover.cap(
          surface.biomeAt(tree.x, tree.y), surface.temperatureAt(tree.x, tree.y), 0.0, isBlighted
        )
        if (cap == BlockType.ICE || cap == BlockType.SNOW) continue

        into.add(
          kind = PropKind.TREE,
          identity = PropId.of(PropKind.TREE, cellX, cellY),
          x = tree.x,
          y = tree.y,
          ground = ground,
          heightM = tree.trunkHeight,
          radiusM = tree.canopyRadius,
          flags = if (isBlighted) PropFlags.BLIGHTED else 0
        )
      }
    }
  }

  /** Lattice cell index of a world coordinate. Integer division of a fixed-point value; see the class KDoc. */
  private fun cellOf(world: Double): Long = Math.floorDiv(Quantize.toFixed(world), cellUnits)

  /** One tree, as the lattice draws it before the ground is known. */
  internal class Tree(val x: Double, val y: Double, val trunkHeight: Double, val canopyRadius: Double)

  private companion object {
    const val TREE_SALT = 0x7A31B0DE4C0F55L
    const val PATCH_SALT = 0x2E9C64B7D1A308L
    const val CLUMP_SALT = 0x5B70E2A9F34C16L

    /** Two octaves: enough that a wood has an irregular edge, few enough that it stays one wood. */
    const val PATCH_OCTAVES = 2

    /**
     * Two, for the same reason [PATCH_OCTAVES] is two, one scale down: a thicket wants a ragged edge
     * and not a fractal one, and a third octave at thirty metres varies inside a single crown.
     */
    const val CLUMP_OCTAVES = 2
  }
}
