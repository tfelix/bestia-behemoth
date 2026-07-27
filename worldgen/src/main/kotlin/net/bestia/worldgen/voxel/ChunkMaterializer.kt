package net.bestia.worldgen.voxel

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.ChunkColumnSource
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.FeatureStore
import net.bestia.worldgen.core.WorldConfig
import java.util.Arrays
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

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
   * Materialises the vertical chunk that contains the ground at a horizontal chunk coordinate.
   *
   * What tooling wants: "show me the ground here" without having to know which of the thirty vertical
   * chunks at this coordinate the ground happens to be in.
   *
   * Anchored on the *lowest* column rather than the middle of the range. That matters: anchoring on the
   * middle loses the valley floor of any chunk whose surface happens to straddle a chunk boundary, and
   * since valleys are exactly what you open this view to look at, the failure is both common and
   * maximally annoying. Anchoring low can only lose ground more than a chunk height *above* the lowest
   * point, which inside thirty-two metres of horizontal extent is a slope no terrain reaches.
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

    // Global voxel indices of the top of each interval. `highest` is the last voxel whose *centre* is at
    // or below the elevation, which is the same rule the rest of the pipeline uses for "is this voxel
    // inside the ground".
    val capTop = highestVoxelAtOrBelow(top)
    val soilTop = capTop - 1
    val rockTop = min(soilTop, highestVoxelAtOrBelow(top - soilDepth))
    val basementTop = min(rockTop, highestVoxelAtOrBelow(rock.basementTop))
    val waterTop = highestVoxelAtOrBelow(water)

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

    if (temperature < FREEZING && waterTop > capTop) {
      val iceBottom = highestVoxelAtOrBelow(water - ICE_THICKNESS)
      cursor = fill(out, offset, baseZ, height, cursor, iceBottom, WATER)
      cursor = fill(out, offset, baseZ, height, cursor, waterTop, ICE)
    } else {
      cursor = fill(out, offset, baseZ, height, cursor, waterTop, WATER)
    }

    Arrays.fill(out.blocks, offset + cursor, offset + height, AIR)

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
        val to = highestVoxelAtOrBelow(deck) - baseZ
        for (localZ in max(0, from)..min(height - 1, to)) {
          out.blocks[offset + localZ] = MASONRY
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

  /** Highest global voxel index whose centre is at or below [elevation]. */
  private fun highestVoxelAtOrBelow(elevation: Double): Int =
    floor(elevation / config.voxelSize - 0.5).toInt()

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
