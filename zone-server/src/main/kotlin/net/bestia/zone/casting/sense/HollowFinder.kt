package net.bestia.zone.casting.sense

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.VoxelChunk
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import org.springframework.stereotype.Component
import kotlin.math.max
import kotlin.math.min

/**
 * Echo Sounding's ear: the hollows under the ground within a radius. A hollow is a connected pocket of air below
 * the surface of its column - a cave, a cellar, a buried chamber. Air above the surface is the sky, not a hollow.
 */
@Component
class HollowFinder(
  private val chunks: ChunkService,
) {

  data class Hollow(val centre: Vec3L, val depth: Int, val size: Int, val voxels: Int)

  /** The hollows within [radius] voxels of [at], largest first, at most [MAX_HOLLOWS]. */
  fun around(at: Vec3L, radius: Int): List<Hollow> {
    val reader = BlockReader()
    val side = 2 * radius + 1
    val surface = IntArray(side * side) { i ->
      chunks.surfaceElevationAt(at.x - radius + i % side, at.y - radius + i / side)?.toInt() ?: Int.MIN_VALUE
    }
    val bottom = at.z - radius
    val top = at.z + radius
    val height = (top - bottom + 1).toInt()
    val seen = BooleanArray(side * side * height)

    fun index(dx: Int, dy: Int, dz: Int) = (dz * side + dy) * side + dx
    fun isHollow(dx: Int, dy: Int, dz: Int): Boolean {
      if (dx !in 0 until side || dy !in 0 until side || dz !in 0 until height) return false
      if ((dx - radius) * (dx - radius) + (dy - radius) * (dy - radius) > radius * radius) return false
      val ground = surface[dy * side + dx]
      val z = bottom + dz
      if (ground == Int.MIN_VALUE || z >= ground - 1) return false

      return reader.at(at.x - radius + dx, at.y - radius + dy, z) == BlockType.AIR
    }

    val hollows = ArrayList<Hollow>()
    for (dz in 0 until height) for (dy in 0 until side) for (dx in 0 until side) {
      if (seen[index(dx, dy, dz)] || !isHollow(dx, dy, dz)) continue
      hollows += flood(dx, dy, dz, seen, ::index, ::isHollow).let { cells ->
        toHollow(cells, at, radius, bottom, side, surface)
      }
    }

    return hollows.filter { it.voxels >= MIN_VOXELS }.sortedByDescending { it.voxels }.take(MAX_HOLLOWS)
  }

  private fun flood(
    x: Int, y: Int, z: Int, seen: BooleanArray,
    index: (Int, Int, Int) -> Int, isHollow: (Int, Int, Int) -> Boolean
  ): List<Triple<Int, Int, Int>> {
    val cells = ArrayList<Triple<Int, Int, Int>>()
    val queue = ArrayDeque(listOf(Triple(x, y, z)))
    seen[index(x, y, z)] = true
    while (queue.isNotEmpty()) {
      val cell = queue.removeFirst()
      cells += cell
      for ((ox, oy, oz) in NEIGHBOURS) {
        val (nx, ny, nz) = Triple(cell.first + ox, cell.second + oy, cell.third + oz)
        if (!isHollow(nx, ny, nz) || seen[index(nx, ny, nz)]) continue
        seen[index(nx, ny, nz)] = true
        queue += Triple(nx, ny, nz)
      }
    }

    return cells
  }

  private fun toHollow(
    cells: List<Triple<Int, Int, Int>>, at: Vec3L, radius: Int, bottom: Long, side: Int, surface: IntArray
  ): Hollow {
    val cx = cells.sumOf { it.first } / cells.size
    val cy = cells.sumOf { it.second } / cells.size
    val cz = cells.sumOf { it.third } / cells.size
    var minX = Int.MAX_VALUE; var maxX = Int.MIN_VALUE; var minY = Int.MAX_VALUE; var maxY = Int.MIN_VALUE
    var topZ = Int.MIN_VALUE
    for ((x, y, z) in cells) {
      minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y); topZ = max(topZ, z)
    }

    return Hollow(
      centre = Vec3L(at.x - radius + cx, at.y - radius + cy, bottom + cz),
      depth = max(0, surface[cy * side + cx] - (bottom + topZ).toInt()),
      size = max(maxX - minX, maxY - minY) + 1,
      voxels = cells.size,
    )
  }

  /** One merged chunk per chunk touched, rather than a store lookup per voxel. */
  private inner class BlockReader {
    private val cache = HashMap<ChunkPos, VoxelChunk>()

    fun at(x: Long, y: Long, z: Long): BlockType? {
      val local = ChunkCoords.localise(chunks.config, x, y, z) ?: return null
      val chunk = cache.getOrPut(local.chunk) { chunks.merged(chunks.normalise(local.chunk)) }

      return chunk[local.localX, local.localY, local.localZ]
    }
  }

  private companion object {
    val NEIGHBOURS = listOf(
      Triple(1, 0, 0), Triple(-1, 0, 0), Triple(0, 1, 0), Triple(0, -1, 0), Triple(0, 0, 1), Triple(0, 0, -1)
    )

    /** Fewer voxels are a crack, not something worth knocking for. */
    const val MIN_VOXELS = 8

    const val MAX_HOLLOWS = 8
  }
}
