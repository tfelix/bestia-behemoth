package net.bestia.worldgen.voxel

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.ChunkColumnSource
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.ColumnHeights
import net.bestia.worldgen.core.FeatureStore
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.karst.CaveParams
import net.bestia.worldgen.resource.GradeMix
import net.bestia.worldgen.vector.Quantize
import java.util.Arrays
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The surface of one horizontal chunk: what is on top of each column, and how full that voxel is.
 *
 * Deliberately not a [VoxelChunk]. The columns here can come from different vertical chunks, so there is no
 * single `z` that the values share and pretending otherwise is what made [ChunkMaterializer.materializeSurface]
 * misleading in the first place.
 */
class SurfaceColumns(
  val size: Int,
  /** Raw block id per column, or -1 where nothing was found at all. */
  val block: IntArray,
  /** Fill fraction of the topmost voxel, or [NO_FILL] where it is not knowable. */
  val fill: DoubleArray,
  /**
   * World elevation of the top of that voxel in metres, or [NO_FILL] where it is not knowable.
   *
   * The value worth testing against, because it is directly comparable with what the column source said the
   * surface height was. A column read off the ceiling of the wrong vertical chunk disagrees with that by tens
   * of metres, which no amount of looking at a colour map reliably shows.
   */
  val elevation: DoubleArray
) {

  fun blockAt(localX: Int, localY: Int) = block[localY * size + localX]

  fun fillAt(localX: Int, localY: Int) = fill[localY * size + localX]

  fun elevationAt(localX: Int, localY: Int) = elevation[localY * size + localX]

  companion object {
    /** No answer, as distinct from a fill of zero. */
    const val NO_FILL = Double.NaN
  }
}

/**
 * Turns column heights into blocks: step 3 of `generate_chunk` in the architecture document.
 *
 * The ordering is fixed here and it matters: natural terrain and vector features first (which
 * [ChunkColumnSource] has already done), then bedrock stratigraphy, then soil, then the surface cap, then
 * water, then what is built on top, then **subtraction**, and **vegetation last of all**. Water before the
 * structures because a lake surface is a property of the world and no structure may carve it; soil after
 * rock, because soil depth is a surface property and the rock beneath it does not care; removal after
 * everything solid, because a hole is defined by the material it is a hole in (see [carve]); and foliage
 * after the removal, because it is the one producer that only ever fills air and therefore has to know what
 * air there is.
 *
 * Nothing in here is chunk-seeded. Every block a column gets is a function of that column's world
 * position and its surface height, which is what makes two chunks agree about the shared column on their
 * border without either knowing the other exists. The architecture document's standing permission for
 * *chunk-seeded* scatter at the end of chunk generation is still unused, and vegetation is the case that
 * shows why it should stay unused: a four-metre canopy spans columns, so a chunk-seeded tree on a border is
 * half a tree. [VegetationScatter] hashes a lattice of quantised world coordinates instead.
 *
 * ### Filled by runs, not by voxels
 *
 * A column is a handful of intervals - basement, then some beds, then soil, one cap block, then water,
 * then air - and every one of them is a contiguous span. Writing them with `Arrays.fill` instead of
 * looping per voxel is roughly an order of magnitude faster and, more usefully, it means the boundaries
 * are computed explicitly rather than rediscovered 256 times by comparing the same two numbers.
 */
class ChunkMaterializer(
  private val config: WorldConfig,
  private val columns: ChunkColumnSource,

  /**
   * The rock column, for the same reason [surface] is visible: "what is the rock under here" is a question
   * about the world rather than about materialising a chunk, and the invariant that checks caves are in
   * limestone would otherwise have to rebuild a second `Stratigraphy` from the same layers and hope it matched.
   */
  val strata: Stratigraphy,

  /**
   * The surface classifier: which biome, and therefore which cap and soil, a world position reads as.
   *
   * Visible rather than private because it answers a question about the world that has nothing to do with
   * materialising a chunk - "what does the ground here read as" - and materialising a whole chunk to find out
   * is both slow and a much larger dependency than the question needs. `probe --ecotone` asks it a million
   * times over a world, which is the only way the biome dither can be measured rather than argued about.
   */
  val surface: SurfaceSampler,
  /**
   * The vector tier, for river water. Rivers are the one water body whose surface is not level, so it
   * cannot come from a raster - see [RiverWaterSampler].
   */
  private val features: FeatureStore,

  /**
   * The cave tuning, forwarded from the stage that placed them rather than defaulted here.
   *
   * A default would compile and would be wrong the moment a params file moved a passage's size or the roof
   * cover it keeps: the stage would place galleries by one rule and the carve would cut them by another.
   */
  private val caveParams: CaveParams = CaveParams(),

  vegetationParams: VegetationParams = VegetationParams(),

  /**
   * How ore splits between the three grades, forwarded from the stage that sized the deposits.
   *
   * Defaulted here only so a test can build a materialiser without a params object. The same argument as
   * [caveParams] applies with more force: the world tier divided a tonnage by this mix's average yield to
   * decide how big every orebody is, so a different mix here means every deposit in the world holds a
   * different amount of metal than the number on its marker.
   */
  private val grades: GradeMix = GradeMix(),

  /**
   * The corruption field, or null on a pipeline without the corruption stage.
   *
   * Reaches exactly one thing: [OreVeins], which samples it once per deposit to decide whether that body
   * yields aetherite. The *cover* does not come through here - it goes through [surface], which carries the
   * layer itself and owns the dither that decides a column.
   */
  private val corruption: FloatLayer? = null,

  /** Corruption at or above which a body yields aetherite. Forwarded from `CorruptionParams`. */
  private val aetheriteCorruption: Double = 1.0,

  /** Where the mana crystals grow. Forwarded from the params object, like the vegetation tuning. */
  crystalParams: CrystalParams = CrystalParams(),

  /** Where the aetherite shards outcrop. Forwarded from the params object, like the crystal tuning. */
  private val aetheriteParams: AetheriteParams = AetheriteParams()
) {

  /**
   * Where the mana crystals are, without materialising a chunk to find out.
   *
   * Public for the reason [surface] is: "is there a crystal here" is a question about the world, and a
   * runtime that wants to respawn a harvested one should not have to build a chunk to ask.
   */
  val crystals = CrystalScatter(config, surface, config.seed, crystalParams)

  /**
   * Where the trees are.
   *
   * Built here from [surface] rather than handed in, and visible for the same reason [surface] and [strata]
   * are: "is there a wood here" is a question about the world. `VegetationStage` rasterises the canopy from
   * this very object, so the kilometre raster and the voxels cannot disagree, and a spawner can ask it about
   * a position without materialising a chunk to find out.
   */
  val vegetation = VegetationScatter(config, surface, config.seed, vegetationParams)

  /** Materialises one chunk volume. */
  fun materialize(chunk: ChunkPos): VoxelChunk {
    val out = VoxelChunk(chunk, config.chunkSize, config.chunkHeight)
    val baseZ = config.voxelBaseOf(chunk)

    // One query for the whole chunk. The index stores bounds already expanded by each feature's influence
    // radius, so the chunk's own bounds are enough - a miss genuinely cannot reach any column here. Point
    // markers have zero-extent bounds, so they need the widest orebody's radius as a margin.
    val nearby = features.query(config.chunkBounds(chunk).expanded(MARKER_MARGIN))
    val rivers = RiverWaterSampler(nearby)
    val ponds = PondWaterSampler(nearby)
    val lava = LavaSampler(nearby)
    val ore = OreVeins(nearby, config.seed, grades, corruption, aetheriteCorruption)
    val bridges = BridgeDecks(nearby)
    val structures = TownStructures(nearby, config.seed)
    val caves = CaveNetwork(nearby, config.seed, caveParams)
    // One buffer for the whole chunk, refilled per column, and shared by both producers. See StructureSpans.
    val spans = if (structures.isEmpty && caves.isEmpty) null else StructureSpans()

    // One column of halo, and it is structural rather than an optimisation: `gradientAt` takes a central
    // difference, so without it the edge columns would fall back to a one-sided difference while the
    // neighbouring chunk took a central one over the same shared world column - a one-voxel stripe of
    // mismatched cap material down every chunk border.
    //
    // It used to be `max(1, if (candidates.isEmpty) 0 else vegetation.halo)`, and that whole expression - and
    // the separate `candidatesIn` pass that fed it - existed because a crown hanging in from outside needed
    // the ground under a trunk in the next chunk. Nothing draws a crown any more, so the halo is one.
    val heights = columns.heights(chunk, 1)

    for (localY in 0 until config.chunkSize) {
      for (localX in 0 until config.chunkSize) {
        val (worldX, worldY) = config.columnCenter(chunk, localX, localY)
        fillColumn(
          out, out.columnOffset(localX, localY), baseZ, worldX, worldY,
          heights[localX, localY], gradientAt(heights, localX, localY),
          rivers, ponds, lava, ore, bridges, structures, caves, spans
        )
      }
    }

    return out
  }

  /**
   * Gradient of the materialised surface at one column, by central differences over one voxel.
   *
   * Of [ColumnHeights] rather than of `LayerId.ELEVATION`, and that is the whole point. The raster knows about
   * mountain fronts and knows nothing about the fjord wall a vector feature cut into one, or the trough wall a
   * glacier left, or a river bank - and those are where the most dramatic cliffs in this world actually are.
   * This is the gradient of the ground the player is standing on, which is the only gradient that can answer
   * "is this bare rock" truthfully.
   *
   * A hard threshold on this is safe where the same threshold on a kilometre raster would not be -
   * `SurfaceSampler.isBlightedAt` argues at length about why a raster threshold draws a visible contour line -
   * because this field varies at voxel scale, so its contours are already as ragged as the ground is.
   */
  private fun gradientAt(heights: ColumnHeights, localX: Int, localY: Int): Double {
    require(heights.halo >= 1) { "a central difference needs a halo of at least one column" }

    val dx = (heights[localX + 1, localY] - heights[localX - 1, localY]) / (2.0 * config.voxelSize)
    val dy = (heights[localX, localY + 1] - heights[localX, localY - 1]) / (2.0 * config.voxelSize)
    return sqrt(dx * dx + dy * dy)
  }

  /**
   * The props whose own position falls inside one chunk column-stack.
   *
   * ### Take the overload that accepts heights wherever you already have them
   *
   * [ColumnHeights] for one chunk is a thousand heightfield evaluations plus a feature pass, and a
   * server streaming terrain has already built and cached exactly that. The convenience overload is for
   * tooling and tests; a hot path that uses it does the heightfield work twice per chunk.
   *
   * ### Addressed by column, not by [ChunkPos]
   *
   * Props are a fact about the *surface*, so a vertical index would either be ignored - inviting a
   * caller to ask once per slab and believe the answer was filtered - or would have to mean something it
   * cannot. Same reason [surfaceColumns] and `GeneratedWorld.contentSlabsOf` take a column.
   *
   * ### Never materialise this over a region
   *
   * A 128 km world holds on the order of a million tree props at the default [VegetationParams.entityShare],
   * and a 4096 km one a thousand times that. This is a *function*, and the precedent that invites the
   * mistake is real: `WildSpawnerService` resolves its whole world's dens into a list at boot, which is
   * right for fourteen hundred markers and would exhaust the heap here.
   */
  fun propsIn(chunkX: Int, chunkY: Int): PropInstances =
    propsIn(chunkX, chunkY, columns.heights(ChunkPos(chunkX, chunkY, 0), 0))

  fun propsIn(chunkX: Int, chunkY: Int, heights: ColumnHeights): PropInstances {
    val chunk = ChunkPos(chunkX, chunkY, 0)
    val nearby = features.query(config.chunkBounds(chunk).expanded(MARKER_MARGIN))

    val structures = TownStructures(nearby, config.seed)
    val caves = CaveNetwork(nearby, config.seed, caveParams)
    val bridges = BridgeDecks(nearby)

    val site = trunkSite(chunk, heights, structures, caves, bridges)
    val into = PropInstances()

    vegetation.propsIn(chunk, site, into)
    crystals.propsIn(chunk, site, into)
    structures.spireProps(config, chunk, site, into)
    // Built here from `nearby` rather than held in a field like `crystals` and `vegetation`, because its input
    // is the deposit markers for *this* query rather than a layer covering the world. Same reason
    // `TownStructures` and `OreVeins` are constructed per chunk a few lines above.
    aetherite(nearby).propsIn(chunk, site, into)

    return into
  }

  /** The aetherite outcrops among one query's features. See [AetheriteScatter]. */
  private fun aetherite(nearby: List<net.bestia.worldgen.vector.VectorFeature>) = AetheriteScatter(
    config = config,
    surface = surface,
    features = nearby,
    seed = config.seed,
    params = aetheriteParams,
    // The same field and the same threshold `OreVeins` is given, so the shard on the grass and the seam under
    // it cannot disagree about whether this body is aetherite.
    corruption = corruption,
    aetheriteCorruption = aetheriteCorruption
  )

  /**
   * What the vegetation scatter is allowed to know about a trunk's surroundings.
   *
   * Everything asked here is a **pure function of the trunk's world position**, never of the column being
   * filled, because the chunk next door draws the other half of the same crown and has to reach the same
   * verdict about the same tree. That is also why the structures and the caves are re-asked at the trunk
   * rather than read off the column: the column is thirty metres away and belongs to a different chunk half
   * the time.
   *
   * The feature sets agree across a border for the reason the heights do. A trunk is at most a few metres
   * outside this chunk and [MARKER_MARGIN] is three hundred, so any feature reaching it was returned by both
   * chunks' queries.
   */
  private fun trunkSite(
    chunk: ChunkPos,
    heights: ColumnHeights,
    structures: TownStructures,
    caves: CaveNetwork,
    bridges: BridgeDecks
  ) = PropSite { worldX, worldY ->
    val localX = Math.floorDiv(Quantize.toFixed(worldX), Quantize.toFixed(config.voxelSize)).toInt() -
        chunk.x * config.chunkSize
    val localY = Math.floorDiv(Quantize.toFixed(worldY), Quantize.toFixed(config.voxelSize)).toInt() -
        chunk.y * config.chunkSize

    val ground = heights[localX, localY]
    val scratch = trunkScratch

    when {
      // A street is ground somebody swept. Checked before anything else because it is one query.
      structures.pavingAt(worldX, worldY) != null -> Double.NaN

      // Nor through a carriageway. This one is an *entity's* veto and was not in the voxel path's set: a
      // crown was written into air only, so it stopped at the decking and read as a tree beside a bridge.
      // A prop is placed at a position and drawn whole, so it grows through the road.
      !bridges.deckAt(worldX, worldY).isNaN() -> Double.NaN

      else -> {
        scratch.clear()
        structures.columnAt(worldX, worldY, ground, scratch)
        val builtOver = scratch.ceiling()
        caves.columnAt(worldX, worldY, ground, builtOver, scratch)

        when {
          // A tree does not grow through somebody's roof, nor out of the collar of a mine shaft.
          !builtOver.isNaN() -> Double.NaN
          // Nor over a hole. This is the one veto a density field could never see: a cave mouth is not a
          // property of the climate, and a tree standing in mid-air over one is the failure it would produce.
          bracketsGround(scratch, ground) -> Double.NaN
          else -> ground
        }
      }
    }
  }

  /** Whether any removal span in [spans] takes away the ground a trunk would stand on. */
  private fun bracketsGround(spans: StructureSpans, ground: Double): Boolean {
    for (i in 0 until spans.count) {
      if (!spans.isRemoval(i)) continue
      if (spans.bottomOf(i) <= ground && spans.topOf(i) > ground - config.voxelSize) return true
    }
    return false
  }

  /**
   * Scratch for [trunkSite], one per thread.
   *
   * A chunk is materialised on whichever worker drew it and `ChunkMaterializer` is shared across all of
   * them, so a plain field here would have two chunks filling one buffer. The alternative - allocating a
   * buffer per chunk and threading it through - is the same object with a longer argument list.
   */
  private val trunkScratchLocal = ThreadLocal.withInitial { StructureSpans() }

  private val trunkScratch get() = trunkScratchLocal.get()

  /**
   * Materialises the vertical chunk that contains the *lowest* ground at a horizontal chunk coordinate.
   *
   * Anchored low so the floor of a valley is never the thing that gets lost, since valleys are what you open
   * a surface view to look at.
   *
   * **This clips columns near the top of its slab, so do not use it to ask what is on the surface** - use
   * [surfaceColumns], which is what tooling wants. The reason is that vertical chunks are grid aligned:
   * anchoring on the lowest column snaps down to a multiple of the chunk height, so the headroom above that
   * column is whatever is left before the next boundary and can be a single voxel rather than a whole chunk.
   * A chunk with thirty metres of relief whose valley floor happens to sit twenty metres below a boundary
   * therefore loses its ridge, and what a caller reads at those columns is the slab ceiling - bedrock, or the
   * water above an ocean floor - reported as though it were the ground.
   */
  fun materializeSurface(chunkX: Int, chunkY: Int): VoxelChunk {
    val heights = columns.heights(ChunkPos(chunkX, chunkY), 0)

    var lowest = Double.MAX_VALUE
    for (localY in 0 until config.chunkSize) {
      for (localX in 0 until config.chunkSize) {
        val h = heights[localX, localY]
        if (h < lowest) lowest = h
      }
    }

    // One voxel of headroom below the lowest column. Without it a column sitting within half a voxel of
    // the chunk floor has its surface voxel in the chunk *below* this one, and comes back as a sliver of
    // empty air - which in the surface view is a hole in the bottom of every valley.
    return materialize(ChunkPos(chunkX, chunkY, config.chunkZOf(lowest - config.voxelSize)))
  }

  /**
   * The topmost non-air voxel of every column at a horizontal chunk coordinate, wherever vertically it lives.
   *
   * What "show me the ground here" actually means, and the honest version of [materializeSurface]: the answer
   * per column is taken from whichever vertical chunk that column's surface is in, so a chunk whose relief
   * straddles a vertical boundary reports its ridge and its valley floor rather than one of them and a lie
   * about the other.
   *
   * Costs at most two materialisations, because a horizontal chunk is thirty-two metres across and terrain
   * does not have a chunk height of relief inside that - the earlier failure was never about relief, it was
   * about grid alignment.
   *
   * A column whose air interface is above both slabs - a deep ocean column, whose water surface can be
   * hundreds of metres up - reports the block at the top of the upper slab, which is the water above it, and
   * [SurfaceColumns.NO_FILL] for its fill. That is the truthful pair: there *is* water over this column, and
   * how full its topmost voxel is is not a question this has looked high enough to answer.
   */
  fun surfaceColumns(chunkX: Int, chunkY: Int): SurfaceColumns {
    val size = config.chunkSize
    val heights = columns.heights(ChunkPos(chunkX, chunkY), 0)

    var lowest = Double.MAX_VALUE
    var highest = -Double.MAX_VALUE
    for (localY in 0 until size) {
      for (localX in 0 until size) {
        val h = heights[localX, localY]
        if (h < lowest) lowest = h
        if (h > highest) highest = h
      }
    }

    val block = IntArray(size * size) { -1 }
    val fill = DoubleArray(size * size) { SurfaceColumns.NO_FILL }
    val elevation = DoubleArray(size * size) { SurfaceColumns.NO_FILL }

    val bottomZ = config.chunkZOf(lowest - config.voxelSize)
    // One slab above the highest column, so that a column whose surface lands in the very top voxel of the
    // highest slab is not mistaken for one whose surface is above everything scanned. Without the extra slab
    // that case reads as "no answer", which at a chunk height of 256 is a rare curiosity and at 16 is one column
    // in sixteen.
    val topZ = config.chunkZOf(highest) + 1

    // Downwards, so the first slab that has anything in a column is the highest one that does.
    for (chunkZ in topZ downTo bottomZ) {
      val slab = materialize(ChunkPos(chunkX, chunkY, chunkZ))
      val baseZ = config.voxelBaseOf(slab.chunk)
      var remaining = 0

      for (localY in 0 until size) {
        for (localX in 0 until size) {
          val i = localY * size + localX
          if (block[i] >= 0) continue

          val z = slab.highestNonAir(localX, localY)
          if (z < 0) {
            remaining++
            continue
          }

          block[i] = slab.rawAt(localX, localY, z)
          // Full to the ceiling of the topmost slab means the interface really is higher than anything scanned -
          // a deep ocean column, whose water surface can be hundreds of metres up - so neither the fill nor the
          // elevation is knowable from here even though the material is.
          if (z == slab.height - 1 && chunkZ == topZ) continue

          fill[i] = slab.fillAt(localX, localY, z)
          elevation[i] = config.elevationOfVoxel(baseZ + z) + fill[i] * config.voxelSize
        }
      }

      if (remaining == 0) break
    }

    return SurfaceColumns(size, block, fill, elevation)
  }

  private fun fillColumn(
    out: VoxelChunk,
    offset: Int,
    baseZ: Int,
    worldX: Double,
    worldY: Double,
    top: Double,
    steepness: Double,
    rivers: RiverWaterSampler,
    ponds: PondWaterSampler,
    lava: LavaSampler,
    ore: OreVeins,
    bridges: BridgeDecks,
    structures: TownStructures,
    caves: CaveNetwork,
    spans: StructureSpans?,
  ) {
    // Three water surfaces, and the highest wins. The raster one is level - sea, or a lake priority-flood
    // found; the river one descends along its channel; the pond one is level too but is not in the raster at
    // all, because the moraine damming it was never rasterised. See `hydro/PondStage.kt`.
    //
    // Taking the highest is what makes the combinations come out right without a single special case. A river
    // running into a lake takes the lake's level, because by the time the channel reaches it the lake surface
    // is the higher of the two. A pond the river feeds behaves the same way, from the other side.
    val standing = surface.waterLevelAt(worldX, worldY)
    val flowing = rivers.surfaceAt(worldX, worldY)
    val impounded = ponds.surfaceAt(worldX, worldY)
    var water = standing
    if (!flowing.isNaN() && flowing > water) water = flowing
    if (!impounded.isNaN() && impounded > water) water = impounded

    /*
     * Lava is **exclusive**, not a fourth surface in the contest above, and that is a structural rule rather
     * than a stylistic one.
     *
     * Water over lava - or the reverse - puts two materials under one air interface, which is the assumption
     * the bulk occupancy fill below is built on: everything written is full, and only the single voxel the
     * interface falls inside is partial. It would also make the ice branch three-way, and it would mean a
     * lake and a lava lake sharing a shoreline that neither sampler knows about. The exclusion is one
     * comparison, and it makes a column either a water column or a lava column and never both.
     *
     * Lava wins where they overlap, which is the only way round that terminates: a pool is stored with an
     * exact `contains` test and a stored surface, so its shoreline is decided identically by every chunk,
     * while the water above it could be the sea, a river or a pond and the three do not agree about which of
     * them is even present.
     */
    val molten = lava.surfaceAt(worldX, worldY)
    val moltenDepth = if (molten.isNaN()) 0.0 else (molten - top).coerceAtLeast(0.0)
    val waterDepth = if (moltenDepth > 0.0) 0.0 else (water - top).coerceAtLeast(0.0)

    /*
     * Whether this column stands under *any* fluid, stated once.
     *
     * Four producers below need it - the bare-rock test, the blight dither, the paving and the carve veto -
     * and each of them had its own `waterDepth <= 0.0`. Naming it is what stops the next fluid from having to
     * be remembered in four places, which is the same argument the carve veto's own comment makes about
     * stating a rule at the call site rather than in each producer.
     */
    val flooded = waterDepth > 0.0 || moltenDepth > 0.0

    val biome = surface.biomeAt(worldX, worldY)
    val temperature = surface.temperatureAt(worldX, worldY)

    // Steep, dry ground carries no soil and shows what it is made of. Measured on the materialised surface
    // rather than read off a biome, which is what this used to do - see `Biome` on why `CLIFF` is gone.
    val steep = !flooded && steepness >= BARE_ROCK_GRADIENT
    // No soil under lava either, and for a better reason than under water: molten rock does not sit on turf.
    // A crater floor is rock all the way down.
    val soilDepth = if (steep || moltenDepth > 0.0) 0.0 else surface.soilDepthAt(worldX, worldY)
    // One dither draw for both the soil and the cap, so a column cannot come out with blighted turf over
    // clean earth. Under water it is always false - corruption is zero over lakes and sea by construction.
    val blighted = !flooded && surface.isBlightedAt(worldX, worldY)
    val soilBlock = SurfaceCover.soil(biome, temperature, blighted).id.toByte()
    // A paved street replaces the surface cap rather than sitting on it - the paving *is* the ground here.
    // Never under water, because a ford is a ford and a cobbled riverbed is not a thing.
    val paving = if (spans != null && !flooded) structures.pavingAt(worldX, worldY) else null

    val height = config.chunkHeight
    val rock = strata.columnAt(worldX, worldY)

    // The floor of a lava lake is chilled basalt, and it outranks everything: no biome cover, no paving and
    // no exposed bed shows through the bottom of one. An override of the cap rather than a fifth argument to
    // `SurfaceCover.cap`, which two other callers share and neither of which has a fluid to tell it about.
    val bed = if (moltenDepth > 0.0) BlockType.BASALT else null

    // Bare rock outranks paving and the cap both: a street is not laid on a cliff face, and grass does not
    // grow on one. `bareCover` answers null for most biomes, which means "show the bed that is exposed here" -
    // so a limestone crag is white and a shale one grey, from the stratigraphy, with no table for it.
    val bare =
      if (!steep) null else SurfaceCover.bareCover(biome) ?: rock.rockAt(top - config.voxelSize * 0.5)
    val capBlock =
      (bed ?: bare ?: paving ?: SurfaceCover.cap(biome, temperature, waterDepth, blighted)).id.toByte()

    /*
     * Two rules for two different kinds of boundary, and the distinction is the whole of the occupancy
     * change.
     *
     * Boundaries *inside* the ground - basement to sediment, bed to bed, rock to soil - separate two
     * materials, and a voxel straddling one has to pick a single material. The rule there is the centre
     * rule: the voxel belongs to whichever material contains its centre. Nothing is lost, because there is
     * no way to represent half a voxel of sandstone and half of limestone anyway.
     *
     * The boundary at the *air interface* is different: it separates a material from nothing, and that is
     * exactly what an occupancy fraction can carry. So it uses the fill rule instead - the top voxel is the
     * one the surface elevation falls inside, and it is filled by however much of it lies below the surface.
     * A surface at 40.3 m is voxel 40 at thirty percent, rather than voxel 39 at a hundred and a tenth of a
     * metre of terrain quietly discarded.
     */
    // Whichever fluid this column holds, as one surface and one material. The generalisation is what keeps
    // the geometry below single-fluid: everything from here down asks "how high does the fluid stand and what
    // is it made of", and the exclusion above is what guarantees those two questions have one answer each.
    val fluidLevel = if (moltenDepth > 0.0) molten else water
    val fluidBlock = if (moltenDepth > 0.0) LAVA else WATER

    val submerged = fluidLevel > top

    // Global voxel indices of the top of each interval.
    val capTop = if (submerged) highestVoxelAtOrBelow(top) else topFilledVoxel(top)
    val soilTop = capTop - 1
    val rockTop = min(soilTop, highestVoxelAtOrBelow(top - soilDepth))
    val basementTop = min(rockTop, highestVoxelAtOrBelow(rock.basementTop))
    val fluidTop =
      if (submerged) topFilledVoxel(fluidLevel) else highestVoxelAtOrBelow(fluidLevel)

    // Basement, as one fill.
    var cursor = fill(out, offset, baseZ, height, 0, basementTop, rock.basementRock.id.toByte())

    // Sedimentary cover, one fill per bed. Beds are at least nine metres thick, so at metre voxels this
    // is a handful of fills rather than a hundred comparisons.
    while (cursor < height && cursor + baseZ <= rockTop) {
      val centre = config.elevationOfVoxel(cursor + baseZ) + config.voxelSize * 0.5
      val bed = rock.bedIndexAt(centre)
      val facies = rock.faciesOf(bed).id.toByte()
      val bedTop = min(rockTop, highestVoxelAtOrBelow(rock.topOfBed(bed)))

      val next = fill(out, offset, baseZ, height, cursor, bedTop, facies)
      cursor = if (next > cursor) {
        next
      } else {
        // A bed thinner than a voxel would advance nothing and spin here forever. Write one and move on.
        out.blocks[offset + cursor] = facies
        cursor + 1
      }
    }

    cursor = fill(out, offset, baseZ, height, cursor, soilTop, soilBlock)
    cursor = fill(out, offset, baseZ, height, cursor, capTop, capBlock)

    // Where the ground stops, so the occupancy below can tell whether any fluid was actually written.
    val groundCursor = cursor

    // Only water freezes, and the guard is `fluidBlock` rather than a temperature exemption for volcanoes: a
    // hotspot summit stands at nearly four kilometres, so lapse rate puts its mean annual temperature well
    // below freezing and this branch would otherwise cap an open lava lake with a metre and a half of ice.
    if (fluidBlock == WATER && temperature < FREEZING && fluidTop > capTop) {
      val iceBottom = highestVoxelAtOrBelow(fluidLevel - ICE_THICKNESS)
      cursor = fill(out, offset, baseZ, height, cursor, iceBottom, WATER)
      cursor = fill(out, offset, baseZ, height, cursor, fluidTop, ICE)
    } else {
      cursor = fill(out, offset, baseZ, height, cursor, fluidTop, fluidBlock)
    }

    Arrays.fill(out.blocks, offset + cursor, offset + height, AIR)

    // Everything written so far is below the air interface and therefore completely filled; only the single
    // voxel the interface falls inside is partial. Air is left at zero, which the fresh array already is.
    Arrays.fill(out.occupancy, offset, offset + cursor, Occupancy.FULL_BYTE)
    if (cursor > 0) {
      // Whichever elevation actually bounds the topmost voxel written. Usually the fluid surface, but a fluid
      // shallower than one voxel rounds away to no fluid voxel at all - and then the top voxel is ground, and
      // filling it to the waterline would report the ground standing up to a voxel higher than it is. A player
      // would be walking on the surface of a puddle.
      val bounding = if (cursor > groundCursor) fluidLevel else top
      out.occupancy[offset + cursor - 1] = Occupancy.byteOf(
        fillFractionOf(bounding, baseZ + cursor - 1)
      )
    }

    // Ore replaces rock in place, so it has to come after the strata are laid rather than during. It also
    // must not eat the soil or the surface cap: an outcrop is bedrock showing through, not ore instead of
    // topsoil.
    if (!ore.isEmpty) {
      val oreTop = min(rockTop, capTop - 1)
      for (localZ in 0..(oreTop - baseZ).coerceAtMost(height - 1)) {
        if (localZ < 0) continue
        val centre = config.elevationOfVoxel(baseZ + localZ) + config.voxelSize * 0.5
        val block = ore.blockAt(worldX, worldY, centre, top) ?: continue
        out.blocks[offset + localZ] = block.id.toByte()
      }
    }

    // The deck goes over the top of everything, including the water it spans. Last, because it is the one
    // thing in a column that is deliberately not made of what is under it.
    if (!bridges.isEmpty) {
      val deck = bridges.deckAt(worldX, worldY)
      if (!deck.isNaN()) {
        writeStructure(out, offset, baseZ, height, deck - bridges.thickness, deck, MASONRY)
      }
    }

    // Buildings, wall circuits and what history left behind, for the same reason and by the same rule: a
    // structure is a surface with air under it, which a heightfield cannot express.
    if (spans != null) {
      spans.clear()
      structures.columnAt(worldX, worldY, top, spans)

      // Caves fill the *same* buffer, after the structures and before either is applied. That ordering is what
      // lets a cave mouth know whether anything stands over it: `ceiling()` skips removals, so at this point
      // it is exactly "what has been built here", which is the veto an entrance needs. A hole opening into
      // somebody's cellar is a hole in their floor.
      caves.columnAt(worldX, worldY, top, spans.ceiling(), spans)

      /*
       * Two passes over the buffer, additions and then removals, rather than one pass in insertion order.
       *
       * Both properties this needs come from the split. A hole is defined by the material it is a hole *in*,
       * so a shaft has to be able to pierce a collar written into the same column - which only works if every
       * addition has landed before any removal is applied. And among removals the outcome is then independent
       * of the order they were authored in, which is the property two chunks materialising the same column
       * from the same features need: they enumerate the same spans, but nothing promises they enumerate them
       * in the same sequence once more than one producer contributes.
       *
       * A priority sort would give the same guarantee at more cost and would invite the idea that spans
       * compete. They do not: within one column one producer authors them in a deliberate order.
       */
      for (i in 0 until spans.count) {
        if (spans.isRemoval(i)) continue
        writeStructure(
          out, offset, baseZ, height,
          spans.bottomOf(i), spans.topOf(i), spans.blockOf(i).toByte()
        )
      }

      // Nothing may open a hole under a standing fluid, and that is stated once here rather than per producer.
      // A shaft, a passage or an entrance under a lake would drain it - the water is raster-level and level,
      // so there is no mechanism anywhere that would fill the hole or lower the lake, and the result is a dry
      // pit with a wall of water standing over it. One veto at the call site cannot be forgotten by the next
      // producer, which a rule repeated in each of them can - and lava is the producer that proves it, since
      // it arrived after every one of those and needed no edit to any of them.
      if (!flooded) {
        for (i in 0 until spans.count) {
          if (!spans.isRemoval(i)) continue
          carve(out, offset, baseZ, height, spans.bottomOf(i), spans.topOf(i))
        }
      }
    }

  }

  /**
   * Takes one vertical span of material out of a column, leaving a floor under it and a roof over it.
   *
   * Runs after the whole column has been assembled and repairs both arrays as it goes, which is the cheap
   * order: nothing after the bulk occupancy fill reads the assembly cursor, so the one-air-interface
   * assumption the fill relies on is left true for assembly and then invalidated by a writer that fixes up
   * what it touches - exactly as [writeStructure] already does additively.
   *
   * ### The floor and the ceiling use different rounding rules, on purpose
   *
   * The **floor** uses the fill rule: the void's floor elevation falls inside some voxel, and that voxel keeps
   * its material with its occupancy cut to however much of it is still below the floor. That is the same
   * treatment the ground's own top voxel gets, and it means a shaft bottom at 40.3 m stands at 40.3 m rather
   * than at 40 or 41. Occupancy is only ever *reduced*, never raised, so carving through a surface voxel that
   * was already partial cannot make it fuller than it was.
   *
   * The **ceiling** uses the centre rule: the topmost voxel removed is the highest one whose centre lies below
   * the void's ceiling, and the voxel above it keeps all of its material. A fractional ceiling would be read
   * by every derived structure as fill-from-below - [VoxelChunk.solidHeightAt], `ColumnSummary` and
   * `WalkableTile` all treat occupancy that way - which is to say **as a standable surface floating inside
   * solid rock**. The cost of rounding instead is up to one voxel of head height at the top of a passage. That
   * is the whole of the asymmetry, and it is not a new rule: these are the two rules [fillColumn] already
   * names.
   *
   * ### Why the chunk invariant survives
   *
   * Every voxel this writes is either `(AIR, EMPTY)` or an existing block at a strictly positive fraction -
   * the floor voxel's fraction is positive because the floor elevation lies *inside* it, and [Occupancy.of]
   * never quantises a positive fraction to [Occupancy.EMPTY]. So air stays empty, material stays non-empty,
   * and [VoxelChunk.validate] and `RleCodec.decode` keep passing without a new `VoxelChunk.set` overload.
   */
  private fun carve(
    out: VoxelChunk,
    offset: Int,
    baseZ: Int,
    height: Int,
    fromElevation: Double,
    toElevation: Double
  ) {
    val floor = topFilledVoxel(fromElevation) - baseZ
    val ceiling = highestVoxelAtOrBelow(toElevation) - baseZ

    if (floor in 0 until height) {
      val i = offset + floor
      if (out.blocks[i] != AIR) {
        val reduced = Occupancy.byteOf(fillFractionOf(fromElevation, baseZ + floor))
        if (Occupancy.unsigned(reduced) < Occupancy.unsigned(out.occupancy[i])) out.occupancy[i] = reduced
      }
    }

    for (localZ in max(0, floor + 1)..min(height - 1, ceiling)) {
      out.blocks[offset + localZ] = AIR
      out.occupancy[offset + localZ] = Occupancy.EMPTY_BYTE
    }
  }

  /**
   * Writes one vertical span of worked material over whatever the column already held.
   *
   * Occupancy has to be written along with the blocks and not left as it was. A span written into air would
   * otherwise be masonry at zero occupancy - which is to say masonry that is not there - and a span written
   * into rock would keep the rock's full occupancy at its top voxel, losing the fractional surface. The top
   * voxel is partial for exactly the reason the ground's top voxel is: it is where a surface crosses it.
   *
   * It writes over *everything*, deliberately: a wall footing sunk into a hillside is meant to replace the
   * rock it is sunk into.
   *
   * It used to take `onlyIntoAir` and `wholeVoxels`, both for the one producer that must not - the vegetation
   * scatter, whose canopy had to fill the air over the ground without eating a roof, and whose topmost leaf
   * voxel had to be whole because a *leaf* has no surface for a fraction to describe. Trees are entities now
   * and both are gone, which is worth recording because of what it restores: **occupancy again means how much
   * of this voxel lies below a surface, without exception.** `ColumnSummary`, `WalkableTile` and
   * `VoxelChunk.solidHeightAt` all already assumed that, and the canopy was the one thing in the world that
   * broke it.
   */
  private fun writeStructure(
    out: VoxelChunk,
    offset: Int,
    baseZ: Int,
    height: Int,
    fromElevation: Double,
    toElevation: Double,
    block: Byte
  ) {
    val from = highestVoxelAtOrBelow(fromElevation) + 1 - baseZ
    val to = topFilledVoxel(toElevation) - baseZ

    for (localZ in max(0, from)..min(height - 1, to)) {
      out.blocks[offset + localZ] = block
      out.occupancy[offset + localZ] =
        if (localZ == to) Occupancy.byteOf(fillFractionOf(toElevation, baseZ + localZ))
        else Occupancy.FULL_BYTE
    }
  }

  /**
   * Fills local voxels from [fromLocal] up to and including global index [throughGlobal].
   *
   * @return the next unwritten local index
   */
  private fun fill(
    out: VoxelChunk,
    offset: Int,
    baseZ: Int,
    height: Int,
    fromLocal: Int,
    throughGlobal: Int,
    block: Byte
  ): Int {
    val end = min(height, throughGlobal - baseZ + 1)
    if (end <= fromLocal) return fromLocal

    Arrays.fill(out.blocks, offset + fromLocal, offset + end, block)
    return end
  }

  /** Highest global voxel index whose centre is at or below [elevation]. For interior material boundaries. */
  private fun highestVoxelAtOrBelow(elevation: Double): Int =
    floor(elevation / config.voxelSize - 0.5).toInt()

  /**
   * Highest global voxel index with any material below [elevation] - the voxel the elevation falls inside.
   *
   * For the air interface. An elevation landing exactly on a voxel boundary belongs to the voxel *below* it,
   * so a surface at exactly 40 m is a completely full voxel 39 rather than a completely empty voxel 40.
   */
  private fun topFilledVoxel(elevation: Double): Int =
    ceil(elevation / config.voxelSize).toInt() - 1

  /** How much of global voxel [globalZ] lies below [elevation], in `[0,1]` before clamping. */
  private fun fillFractionOf(elevation: Double, globalZ: Int): Double =
    (elevation - config.elevationOfVoxel(globalZ)) / config.voxelSize

  companion object {

    /**
     * Bump on any change to what a column materialises into, so cached chunks are invalidated.
     *
     * The chunk tier's equivalent of `Stage.version`, and it exists because the tier did not have one. Stages
     * each carry a hand-written number that reaches `pipelineVersion` and therefore the chunk cache key; the
     * tier that turns their output into the blocks a player stands on reached that key only through its
     * *params* - so changing the materialisation **code** left every cached chunk looking valid. Adding
     * subtraction changed every mine head in every world and would have moved no number at all.
     *
     * Hand-incremented rather than hashed for the reason [net.bestia.worldgen.voxel.OreVeins] and the strata
     * are not: there is nothing here to hash. It is a statement that behaviour changed, and only a person
     * knows that. `WorldParams.chunkTierVersion` folds it in.
     *
     * 1 was everything up to and including town structures as pure additions.
     */
    // 2: subtraction - StructureSpans.remove and carve, and the mine head as an open shaft.
    // 3: vegetation - LOG and LEAVES scattered from the lattice, written into air after everything else.
    // 4: (see git history)
    // 5: blighted cover - corrupted ground caps and fills with the BLIGHTED_* twins, and its trees with them.
    // 6: wounds - SiteKind.WOUND materialises a blighted rampart and a field of MANA_CRYSTAL_LARGE spires.
    //    No BlockType changed, so `ChunkEngine.VERSION` deliberately stays where it is and the client needs no
    //    release: every block a wound is made of was already in the palette at version 2.
    // 7: bare rock - a column steeper than BARE_ROCK_GRADIENT carries no soil and caps with SurfaceCover
    //    .bareCover, or with the exposed bed where that answers null. Replaces the `CLIFF` biome, which capped
    //    every steep cell in the world in one grey GRAVEL. No BlockType changed here either.
    // 8: lava - a FeatureKind.LAVA_POOL fills its crater with LAVA over a BASALT floor, exclusive of water, and
    //    vetoes the carve the way standing water already did.
    // 9: trees and mana crystals leave the voxel grid. LOG, LEAVES, MANA_CRYSTAL_SMALL/LARGE and the two
    //    BLIGHTED_* twins are deleted and emitted as props for a runtime to make entities of; the wound spires
    //    go with them. The second `StructureSpans` buffer, `writeStructure`'s `onlyIntoAir` and `wholeVoxels`,
    //    and the whole `candidatesIn`/`plant` halo go too. **Unlike 6, 7 and 8 this does change `BlockType`**,
    //    so `ChunkEngine.VERSION` moves with it and the client needs a release.
    const val VERSION = 9

    /**
     * Margin added to a chunk's bounds when querying features, in metres.
     *
     * Point markers store zero-extent bounds - a point has no extent - so unlike a river they cannot be found
     * by a bounds intersection alone. This has to cover the widest orebody, the longest bridge span, and the
     * largest ruin field: a marker whose structure reaches further than this is simply missing from every
     * chunk more than this far from its centre, which reads as a ruin with a straight edge.
     *
     * `Invariants.checkStructuralMarkersFitTheQueryMargin` is the tripwire.
     */
    const val MARKER_MARGIN = 320.0

    private val AIR = BlockType.AIR.id.toByte()
    private val WATER = BlockType.WATER.id.toByte()
    private val LAVA = BlockType.LAVA.id.toByte()
    private val ICE = BlockType.ICE.id.toByte()
    private val MASONRY = BlockType.MASONRY.id.toByte()

    /** Mean annual temperature below which standing water carries permanent ice. */
    const val FREEZING = -2.0

    const val ICE_THICKNESS = 1.5

    /**
     * Surface gradient above which a column is bare rock: no soil, and the bed or the biome's bare cover on
     * top. Measured over one voxel by [gradientAt].
     *
     * **0.7 is the angle of repose**, near enough - thirty-five degrees, just past the thirty to thirty-four
     * at which loose material stops staying where it is put. That is the physical reason a face is bare, so it
     * is the reason this number is what it is rather than a quantile somebody liked the look of.
     *
     * It also lands where the measurement says it should. `probe --steepness` prints the survival curve of this
     * exact gradient over the world; on a 256-cell world at seed 42, over 2.2 million dry land columns with a
     * median gradient of 0.048:
     *
     * ```
     *   0.30  10.016%      0.70   1.129%      1.25   0.113%
     *   0.40   6.324%      0.85   0.359%      1.50   0.107%
     *   0.50   4.119%      1.00   0.248%
     * ```
     *
     * Two things to read off that. The share collapses threefold between 0.70 and 0.85 and then barely moves
     * from 1.25 to 1.50, so the tail past about 1.25 is a fixed population of genuinely vertical faces - mostly
     * cut by vector features - and a threshold up there selects only those, which is a tenth of a per cent and
     * effectively invisible. And below 0.5 the share runs into double figures, which is no longer cliffs but
     * ordinary hillside, and would read as speckle rather than as rock.
     *
     * If it ever does speckle, **widen the baseline in [gradientAt] rather than raising this**: a wider
     * baseline attenuates the fine detail octaves and leaves real cliffs exactly where they are, while a higher
     * threshold throws away real cliffs to hide the noise.
     *
     * Not the same number as `BiomeParams.bareRockSlope`, and not derived from it. That one is a kilometre
     * average deciding whether soil stays on a *cell*; this is a one-voxel gradient deciding what a player sees
     * underfoot, and a kilometre mean of 0.45 already contains faces far steeper than this.
     */
    const val BARE_ROCK_GRADIENT = 0.7
  }
}
