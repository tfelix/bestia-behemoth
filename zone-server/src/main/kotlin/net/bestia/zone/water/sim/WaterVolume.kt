package net.bestia.zone.water.sim

import net.bestia.worldgen.core.ChunkPos

/**
 * The chunks the water simulation holds, linked to each other so a cell can reach across a chunk border.
 *
 * Neighbours are named through [normalise], because on a wrapped world two addresses can name one chunk.
 */
class WaterVolume(private val normalise: (ChunkPos) -> ChunkPos = { it }) {

  private val chunks = HashMap<ChunkPos, WaterChunk>()

  /** Chunks the water pressed against that are not held, for the caller to load. */
  val wanted = LinkedHashSet<ChunkPos>()

  val size: Int
    get() = chunks.size

  operator fun get(pos: ChunkPos): WaterChunk? {
    return chunks[pos]
  }

  fun all(): Collection<WaterChunk> {
    return chunks.values
  }

  fun add(chunk: WaterChunk) {
    require(chunk.pos !in chunks) { "${chunk.pos} is already held" }
    chunks[chunk.pos] = chunk
    wanted.remove(chunk.pos)

    for (face in Face.entries) {
      val neighbour = chunks[neighbourOf(chunk.pos, face)] ?: continue
      chunk.neighbours[face.ordinal] = neighbour
      neighbour.neighbours[opposite(face).ordinal] = chunk
    }
  }

  fun remove(pos: ChunkPos): WaterChunk? {
    val chunk = chunks.remove(pos) ?: return null

    for (face in Face.entries) {
      chunk.neighbours[face.ordinal]?.neighbours?.set(opposite(face).ordinal, null)
      chunk.neighbours[face.ordinal] = null
    }
    return chunk
  }

  fun neighbourOf(pos: ChunkPos, face: Face): ChunkPos {
    return normalise(ChunkPos(pos.x + face.dx, pos.y + face.dy, pos.z + face.dz))
  }

  private fun opposite(face: Face): Face {
    return when (face) {
      Face.DOWN -> Face.UP
      Face.UP -> Face.DOWN
      Face.WEST -> Face.EAST
      Face.EAST -> Face.WEST
      Face.SOUTH -> Face.NORTH
      Face.NORTH -> Face.SOUTH
    }
  }
}
