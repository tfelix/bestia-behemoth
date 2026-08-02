package net.bestia.worldgen.voxel

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.ChunkColumnSource
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.ColumnHeights
import net.bestia.worldgen.core.FeatureStore
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.karst.CaveParams
import net.bestia.worldgen.resource.GradeMix
import net.bestia.worldgen.vector.Quantize
import java.util.Arrays
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

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
  private val grades: GradeMix = GradeMix()
) {

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
    val ore = OreVeins(nearby, config.seed, grades)
    val bridges = BridgeDecks(nearby)
    val structures = TownStructures(nearby, config.seed)
    val caves = CaveNetwork(nearby, config.seed, caveParams)
    // One buffer for the whole chunk, refilled per column, and shared by both producers. See StructureSpans.
    val spans = if (structures.isEmpty && caves.isEmpty) null else StructureSpans()

    // Asked before the heights, which is the whole reason it is a separate call: a crown hanging into this
    // chunk from outside needs the ground under a trunk that is not in it, and that costs a halo - seventy
    // per cent more heightfield evaluations. Most chunks have no trees, and this says so for the price of a
    // hash per four-metre cell.
    val candidates = vegetation.candidatesIn(chunk)
    val heights = columns.heights(chunk, if (candidates.isEmpty) 0 else vegetation.halo)

    val trees = if (candidates.isEmpty) {
      null
    } else {
      vegetation.plant(candidates, trunkSite(chunk, heights, structures, caves))
    }
    // A second buffer, not the shared one: a column in a wood can be under several crowns at once, and
    // crowding them into the eight spans a building and a passage already share would truncate whichever
    // came last. They are also applied at a different time - see [fillColumn].
    val foliage = if (trees == null || trees.isEmpty) null else StructureSpans()

    for (localY in 0 until config.chunkSize) {
      for (localX in 0 until config.chunkSize) {
        val (worldX, worldY) = config.columnCenter(chunk, localX, localY)
        fillColumn(
          out, out.columnOffset(localX, localY), baseZ, worldX, worldY,
          heights[localX, localY], rivers, ponds, ore, bridges, structures, caves, spans, trees, foliage
        )
      }
    }

    return out
  }

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
    caves: CaveNetwork
  ) = VegetationScatter.TrunkSite { worldX, worldY ->
    val localX = Math.floorDiv(Quantize.toFixed(worldX), Quantize.toFixed(config.voxelSize)).toInt() -
        chunk.x * config.chunkSize
    val localY = Math.floorDiv(Quantize.toFixed(worldY), Quantize.toFixed(config.voxelSize)).toInt() -
        chunk.y * config.chunkSize

    val ground = heights[localX, localY]
    val scratch = trunkScratch

    when {
      // A street is ground somebody swept. Checked before anything else because it is one query.
      structures.pavingAt(worldX, worldY) != null -> Double.NaN

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
    rivers: RiverWaterSampler,
    ponds: PondWaterSampler,
    ore: OreVeins,
    bridges: BridgeDecks,
    structures: TownStructures,
    caves: CaveNetwork,
    spans: StructureSpans?,
    trees: TreeLattice?,
    foliage: StructureSpans?
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

    val waterDepth = (water - top).coerceAtLeast(0.0)
    val biome = surface.biomeAt(worldX, worldY)
    val temperature = surface.temperatureAt(worldX, worldY)

    val soilDepth = if (biome == Biome.CLIFF) 0.0 else surface.soilDepthAt(worldX, worldY)
    val soilBlock = SurfaceCover.soil(biome, temperature).id.toByte()
    // A paved street replaces the surface cap rather than sitting on it - the paving *is* the ground here.
    // Never under water, because a ford is a ford and a cobbled riverbed is not a thing.
    val paving = if (spans != null && waterDepth <= 0.0) structures.pavingAt(worldX, worldY) else null
    val capBlock = (paving ?: SurfaceCover.cap(biome, temperature, waterDepth)).id.toByte()

    val height = config.chunkHeight
    val rock = strata.columnAt(worldX, worldY)

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
    val submerged = water > top
    val airInterface = if (submerged) water else top

    // Global voxel indices of the top of each interval.
    val capTop = if (submerged) highestVoxelAtOrBelow(top) else topFilledVoxel(top)
    val soilTop = capTop - 1
    val rockTop = min(soilTop, highestVoxelAtOrBelow(top - soilDepth))
    val basementTop = min(rockTop, highestVoxelAtOrBelow(rock.basementTop))
    val waterTop = if (submerged) topFilledVoxel(water) else highestVoxelAtOrBelow(water)

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

    // Where the ground stops, so the occupancy below can tell whether any water was actually written.
    val groundCursor = cursor

    if (temperature < FREEZING && waterTop > capTop) {
      val iceBottom = highestVoxelAtOrBelow(water - ICE_THICKNESS)
      cursor = fill(out, offset, baseZ, height, cursor, iceBottom, WATER)
      cursor = fill(out, offset, baseZ, height, cursor, waterTop, ICE)
    } else {
      cursor = fill(out, offset, baseZ, height, cursor, waterTop, WATER)
    }

    Arrays.fill(out.blocks, offset + cursor, offset + height, AIR)

    // Everything written so far is below the air interface and therefore completely filled; only the single
    // voxel the interface falls inside is partial. Air is left at zero, which the fresh array already is.
    Arrays.fill(out.occupancy, offset, offset + cursor, Occupancy.FULL_BYTE)
    if (cursor > 0) {
      // Whichever elevation actually bounds the topmost voxel written. Usually [airInterface], but water
      // shallower than one voxel rounds away to no water voxel at all - and then the top voxel is ground, and
      // filling it to the waterline would report the ground standing up to a voxel higher than it is. A player
      // would be walking on the surface of a puddle.
      val bounding = if (cursor > groundCursor) water else top
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

      // Nothing may open a hole under standing water, and that is stated once here rather than per producer.
      // A shaft, a passage or an entrance under a lake would drain it - the water is raster-level and level,
      // so there is no mechanism anywhere that would fill the hole or lower the lake, and the result is a dry
      // pit with a wall of water standing over it. One veto at the call site cannot be forgotten by the next
      // producer, which a rule repeated in each of them can.
      if (waterDepth <= 0.0) {
        for (i in 0 until spans.count) {
          if (!spans.isRemoval(i)) continue
          carve(out, offset, baseZ, height, spans.bottomOf(i), spans.topOf(i))
        }
      }
    }

    /*
     * Trees, last of everything, and the ordering is the same argument the carve makes from the other side.
     *
     * A canopy is the one thing in a column that must not displace what is already there: it is written
     * `onlyIntoAir`, so it fills the space over the ground rather than replacing a roof, a bridge deck or a
     * cliff face it happens to overlap. That only means anything once every other producer has written -
     * including the carve, so that a crown can hang into the mouth of a cave rather than plugging it.
     *
     * There is no veto list here. Every reason not to plant a tree was applied at its own trunk, where it is
     * a pure function of position that the chunk next door reaches the same answer to - see
     * `VegetationScatter.plant` and [trunkSite]. A per-column veto would be a rule the two halves of a
     * straddling crown could disagree about.
     */
    if (trees != null && foliage != null) {
      foliage.clear()
      trees.columnAt(worldX, worldY, foliage)

      for (i in 0 until foliage.count) {
        writeStructure(
          out, offset, baseZ, height,
          foliage.bottomOf(i), foliage.topOf(i), foliage.blockOf(i).toByte(),
          onlyIntoAir = true, wholeVoxels = true
        )
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
   * It writes over *everything* by default, deliberately: a wall footing sunk into a hillside is meant to
   * replace the rock it is sunk into. Both parameters below are for the one producer that must not - the
   * vegetation scatter - and they are parameters here rather than a second writer so that the rounding rules
   * above cannot drift between two copies.
   *
   * @param onlyIntoAir leaves any voxel that already holds material alone. A canopy fills the space over the
   *   ground; it does not eat a roof, a bridge deck or a cliff face it happens to overlap, and it does not
   *   plug the mouth of the cave the carve just opened underneath it.
   * @param wholeVoxels fills the topmost voxel completely instead of to the fraction the span's top
   *   elevation reaches. Occupancy exists to recover a continuous *surface* from voxels, and a leaf canopy
   *   has none - a fractional top leaf is not half a leaf, it is a surface net told to draw a smooth green
   *   dome over the wood. Only the occupancy changes; both bounds round exactly as they do for masonry, so a
   *   crown reads up to a voxel taller than its nominal top and nothing else moves.
   */
  private fun writeStructure(
    out: VoxelChunk,
    offset: Int,
    baseZ: Int,
    height: Int,
    fromElevation: Double,
    toElevation: Double,
    block: Byte,
    onlyIntoAir: Boolean = false,
    wholeVoxels: Boolean = false
  ) {
    val from = highestVoxelAtOrBelow(fromElevation) + 1 - baseZ
    val to = topFilledVoxel(toElevation) - baseZ

    for (localZ in max(0, from)..min(height - 1, to)) {
      if (onlyIntoAir && out.blocks[offset + localZ] != AIR) continue

      out.blocks[offset + localZ] = block
      out.occupancy[offset + localZ] =
        if (localZ == to && !wholeVoxels) Occupancy.byteOf(fillFractionOf(toElevation, baseZ + localZ))
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
    const val VERSION = 4

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
    private val ICE = BlockType.ICE.id.toByte()
    private val MASONRY = BlockType.MASONRY.id.toByte()

    /** Mean annual temperature below which standing water carries permanent ice. */
    const val FREEZING = -2.0

    const val ICE_THICKNESS = 1.5
  }
}
