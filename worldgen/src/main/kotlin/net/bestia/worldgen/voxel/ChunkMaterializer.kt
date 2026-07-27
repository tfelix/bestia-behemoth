package net.bestia.worldgen.voxel

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.ChunkColumnSource
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.FeatureStore
import net.bestia.worldgen.core.WorldConfig
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
 * water. Water last, because a lake surface is a property of the world and nothing may carve it; soil
 * after rock, because soil depth is a surface property and the rock beneath it does not care.
 *
 * Nothing in here is chunk-seeded. Every block a column gets is a function of that column's world
 * position and its surface height, which is what makes two chunks agree about the shared column on their
 * border without either knowing the other exists. Chunk-seeded scatter - which the architecture document
 * puts at the end of chunk generation, and which is safe there precisely because it comes after all
 * geometry - is not implemented; there is no vegetation in the block palette to place yet.
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
  private val strata: Stratigraphy,
  private val surface: SurfaceSampler,
  /**
   * The vector tier, for river water. Rivers are the one water body whose surface is not level, so it
   * cannot come from a raster - see [RiverWaterSampler].
   */
  private val features: FeatureStore
) {

  /** Materialises one chunk volume. */
  fun materialize(chunk: ChunkPos): VoxelChunk {
    val out = VoxelChunk(chunk, config.chunkSize, config.chunkHeight)
    val heights = columns.heights(chunk, 0)
    val baseZ = config.voxelBaseOf(chunk)

    // One query for the whole chunk. The index stores bounds already expanded by each feature's influence
    // radius, so the chunk's own bounds are enough - a miss genuinely cannot reach any column here. Point
    // markers have zero-extent bounds, so they need the widest orebody's radius as a margin.
    val nearby = features.query(config.chunkBounds(chunk).expanded(MARKER_MARGIN))
    val rivers = RiverWaterSampler(nearby)
    val ore = OreVeins(nearby, config.seed)
    val bridges = BridgeDecks(nearby)

    for (localY in 0 until config.chunkSize) {
      for (localX in 0 until config.chunkSize) {
        val (worldX, worldY) = config.columnCenter(chunk, localX, localY)
        fillColumn(
          out, out.columnOffset(localX, localY), baseZ, worldX, worldY,
          heights[localX, localY], rivers, ore, bridges
        )
      }
    }

    return out
  }

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
    ore: OreVeins,
    bridges: BridgeDecks
  ) {
    // Two water surfaces, and the higher wins. The raster one is level - sea or a lake; the river one
    // descends along its channel. A river running into a lake correctly takes the lake's level, because
    // the lake surface is the higher of the two by the time the channel reaches it.
    val standing = surface.waterLevelAt(worldX, worldY)
    val flowing = rivers.surfaceAt(worldX, worldY)
    val water = if (flowing.isNaN() || flowing < standing) standing else flowing

    val waterDepth = (water - top).coerceAtLeast(0.0)
    val biome = surface.biomeAt(worldX, worldY)
    val temperature = surface.temperatureAt(worldX, worldY)

    val soilDepth = if (biome == Biome.CLIFF) 0.0 else surface.soilDepthAt(worldX, worldY)
    val soilBlock = SurfaceCover.soil(biome, temperature).id.toByte()
    val capBlock = SurfaceCover.cap(biome, temperature, waterDepth).id.toByte()

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
        val from = highestVoxelAtOrBelow(deck - bridges.thickness) + 1 - baseZ
        val to = topFilledVoxel(deck) - baseZ
        for (localZ in max(0, from)..min(height - 1, to)) {
          out.blocks[offset + localZ] = MASONRY
          // A deck is written over whatever was there, air included, so its occupancy has to be written too
          // - leaving air's zero behind would be masonry that is not there. The running surface is the top
          // of the deck, so that voxel is partial for the same reason the ground's is.
          out.occupancy[offset + localZ] =
            if (localZ == to) Occupancy.byteOf(fillFractionOf(deck, baseZ + localZ)) else Occupancy.FULL_BYTE
        }
      }
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

  private companion object {
    val AIR = BlockType.AIR.id.toByte()
    val WATER = BlockType.WATER.id.toByte()
    val ICE = BlockType.ICE.id.toByte()
    val MASONRY = BlockType.MASONRY.id.toByte()

    /**
     * Margin added to a chunk's bounds when querying features, in metres.
     *
     * Point markers store zero-extent bounds - a point has no extent - so unlike a river they cannot be found
     * by a bounds intersection alone. This has to cover the widest orebody and the longest bridge span.
     */
    const val MARKER_MARGIN = 320.0

    /** Mean annual temperature below which standing water carries permanent ice. */
    const val FREEZING = -2.0

    const val ICE_THICKNESS = 1.5
  }
}
