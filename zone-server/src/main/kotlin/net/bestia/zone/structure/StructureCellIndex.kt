package net.bestia.zone.structure

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.derived.Edges
import net.bestia.worldgen.derived.StructureCells
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.zone.world.stream.ChunkCoords

/**
 * The cells of every standing building piece, by chunk, and an immutable [StructureCells] snapshot per chunk for
 * whoever builds walk tables from them.
 *
 * Not thread safe: owned by the tick thread. A snapshot may go to any thread.
 */
class StructureCellIndex(
  private val config: WorldConfig,
  private val normalise: (ChunkPos) -> ChunkPos
) {

  private class LocalCell(
    val chunk: ChunkPos,
    val localX: Int,
    val localY: Int,
    val localZ: Int,
    val fill: Int,
    val edges: Int
  )

  private val cellsByPiece = HashMap<Long, List<LocalCell>>()
  private val piecesByChunk = HashMap<ChunkPos, MutableSet<Long>>()
  private val snapshots = HashMap<ChunkPos, StructureCells>()

  val pieceCount: Int
    get() {
      return cellsByPiece.size
    }

  /**
   * Puts a piece's cells in, replacing any it had.
   *
   * @return the chunks whose cells changed
   */
  fun put(pieceId: Long, cells: List<StructureCell>): Set<ChunkPos> {
    val changed = remove(pieceId).toMutableSet()

    val local = cells.map(::localise)
    cellsByPiece[pieceId] = local
    for (cell in local) {
      piecesByChunk.getOrPut(cell.chunk) { HashSet() }.add(pieceId)
      changed.add(cell.chunk)
    }
    changed.forEach(snapshots::remove)

    return changed
  }

  /** @return the chunks whose cells changed, none if the piece was not here */
  fun remove(pieceId: Long): Set<ChunkPos> {
    val cells = cellsByPiece.remove(pieceId) ?: return emptySet()

    val changed = cells.mapTo(HashSet()) { it.chunk }
    for (chunk in changed) {
      val pieces = piecesByChunk.getValue(chunk)
      pieces.remove(pieceId)
      if (pieces.isEmpty()) piecesByChunk.remove(chunk)
      snapshots.remove(chunk)
    }

    return changed
  }

  /** The built cells of a chunk, or null where nobody built. */
  fun cellsOf(chunk: ChunkPos): StructureCells? {
    snapshots[chunk]?.let { return it }

    val pieces = piecesByChunk[chunk] ?: return null
    val builder = StructureCells.Builder(config.chunkSize)
    for (pieceId in pieces) {
      for (cell in cellsByPiece.getValue(pieceId)) {
        if (cell.chunk == chunk) builder.add(cell.localX, cell.localY, cell.localZ, cell.fill, cell.edges)
      }
    }

    return builder.build().also { snapshots[chunk] = it }
  }

  /** Whether any of [cells] fills a cell another piece fills, or puts a wall where one already stands. */
  fun isTaken(cells: List<StructureCell>): Boolean {
    return cells.any { cell ->
      val local = localise(cell)
      val standing = cellsOf(local.chunk) ?: return@any false

      val bothFilled = cell.fill != Occupancy.EMPTY &&
          standing.fillAt(local.localX, local.localY, local.localZ) != Occupancy.EMPTY
      val sameWall = standing.edgesAt(local.localX, local.localY, local.localZ) and cell.edges != Edges.NONE

      bothFilled || sameWall
    }
  }

  private fun localise(cell: StructureCell): LocalCell {
    val at = requireNotNull(ChunkCoords.localise(config, cell.x, cell.y, cell.z)) { "$cell is outside the world" }

    return LocalCell(normalise(at.chunk), at.localX, at.localY, at.localZ, cell.fill, cell.edges)
  }
}
