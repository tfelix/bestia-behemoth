package net.bestia.zone.item.mining

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.CarveBrush
import net.bestia.worldgen.voxel.VoxelChunk
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkStreamInbox
import org.springframework.stereotype.Component

/**
 * The ore below a mining shaft, and digging it out one carve at a time.
 *
 * A shaft digs real voxels, so the ore it hands out leaves the world's finite supply exactly once, whoever digs
 * there later. It digs from the top down, like a miner following a vein. Tick thread only, like [ChunkService].
 */
@Component
class ShaftOre(
  private val chunks: ChunkService,
  private val inbox: ChunkStreamInbox,
  private val oreYield: OreYield,
) {

  /** How far below and around its shaft a miner reaches. */
  data class Reach(val radius: Long, val depth: Long)

  private class Scan(val reach: Reach, val ore: ArrayDeque<Vec3L>)

  /** Shaft id -> the ore it has found and not dug yet, top first. */
  private val scans = HashMap<Long, Scan>()

  /** The ore blocks that come out as an item. Digging the others would only waste them. */
  private val yieldingBlocks: Set<BlockType> by lazy {
    BlockType.entries.filter { oreYield.of(it) != null }.toSet()
  }

  /** Whether the shaft at [shaftAt] has ore left within [reach]. */
  fun hasOre(shaftId: Long, shaftAt: Vec3L, reach: Reach): Boolean {
    return nextOre(shaftId, shaftAt, reach) != null
  }

  /**
   * Digs out the next ore below the shaft, for [diggerId] to receive. The carve lands with the next world tick.
   *
   * @return how many ore voxels the carve takes out, 0 when the shaft has nothing left to dig
   */
  fun dig(shaftId: Long, shaftAt: Vec3L, reach: Reach, ownerAccountId: Long, diggerId: EntityId): Int {
    val target = nextOre(shaftId, shaftAt, reach) ?: return 0
    val brush = CarveBrush.sphere(target.x + 0.5, target.y + 0.5, target.z + 0.5, CarveBrush.MIN_RADIUS)

    val ore = scans.getValue(shaftId).ore
    val taken = ore.filter { brush.removedFractionOf(it.x, it.y, it.z.toInt()) >= 1.0 }
    ore.removeAll(taken.toSet())

    inbox.offerCarve(ChunkStreamInbox.Carve(ownerAccountId, brush, yieldTo = diggerId))

    return taken.size
  }

  /** Drops what is known about a shaft that is gone. */
  fun forget(shaftId: Long) {
    scans.remove(shaftId)
  }

  /** The topmost ore still in the ground, skipping what someone else dug out since the scan. */
  private fun nextOre(shaftId: Long, shaftAt: Vec3L, reach: Reach): Vec3L? {
    val scan = scans[shaftId]?.takeIf { it.reach == reach } ?: scan(shaftAt, reach).also { scans[shaftId] = it }

    while (scan.ore.isNotEmpty()) {
      val next = scan.ore.first()
      if (blockAt(next) in yieldingBlocks) return next
      scan.ore.removeFirst()
    }

    return null
  }

  private fun scan(shaftAt: Vec3L, reach: Reach): Scan {
    val top = shaftAt.z - TOP_CLEARANCE
    val bottom = shaftAt.z - reach.depth
    val read = HashMap<ChunkPos, VoxelChunk>()
    val found = ArrayList<Vec3L>()

    for (dx in -reach.radius..reach.radius) {
      for (dy in -reach.radius..reach.radius) {
        if (dx * dx + dy * dy > reach.radius * reach.radius) continue

        for (z in bottom..top) {
          val voxel = Vec3L(shaftAt.x + dx, shaftAt.y + dy, z)
          if (blockAt(voxel, read) in yieldingBlocks) found += voxel
        }
      }
    }

    val topFirst = found.sortedWith(compareByDescending<Vec3L> { it.z }.thenBy { it.distanceSquaredXY(shaftAt) })
    return Scan(reach, ArrayDeque(topFirst))
  }

  private fun blockAt(voxel: Vec3L, read: MutableMap<ChunkPos, VoxelChunk> = HashMap()): BlockType? {
    val local = ChunkCoords.localise(chunks.config, voxel.x, voxel.y, voxel.z) ?: return null
    val chunk = read.getOrPut(chunks.normalise(local.chunk)) { chunks.merged(chunks.normalise(local.chunk)) }

    return chunk[local.localX, local.localY, local.localZ]
  }

  private fun Vec3L.distanceSquaredXY(other: Vec3L): Long {
    val dx = x - other.x
    val dy = y - other.y
    return dx * dx + dy * dy
  }

  companion object {
    /** Around the shaft, in voxels: room for a few galleries side by side. */
    const val RADIUS = 8L

    /** How much deeper each level of Mining lets a miner dig, in voxels. */
    const val DEPTH_PER_LEVEL = 40L

    /**
     * Rock left above the topmost carve. A carve is a ball of [CarveBrush.MIN_RADIUS], and a shaft that dug the
     * ground away under its own frame would undermine itself.
     */
    const val TOP_CLEARANCE = 4L

    /** How far a master with [miningLevel] reaches, or null when they cannot mine at all. */
    fun reachAt(miningLevel: Int): Reach? {
      if (miningLevel < 1) return null

      return Reach(RADIUS, DEPTH_PER_LEVEL * miningLevel)
    }
  }
}
