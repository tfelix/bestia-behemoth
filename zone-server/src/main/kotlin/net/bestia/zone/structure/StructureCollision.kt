package net.bestia.zone.structure

import jakarta.annotation.PostConstruct
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.zone.aoi.AoiLayer
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import org.springframework.stereotype.Service

/**
 * Makes standing building pieces block movement: keeps their cells, and rebuilds the walk tables they touch.
 *
 * Tick thread only, like [ChunkService].
 */
@Service
class StructureCollision(
  private val chunkService: ChunkService,
  private val catalogue: StructurePieceCatalogue,
  private val aoi: EntityAOIService
) {

  /** Built on first use: the world's chunk shape is only known once it has loaded. */
  private val index by lazy { StructureCellIndex(chunkService.config, chunkService::normalise) }

  @PostConstruct
  fun register() {
    chunkService.useStructures { chunk -> index.cellsOf(chunk) }
  }

  fun isPiece(kind: StaticEntityKind): Boolean {
    return catalogue.isPiece(kind)
  }

  /**
   * Whether a piece fits at [origin]: none of its cells is taken by another piece or by terrain, and nobody
   * stands where it would fill a cell.
   */
  fun fits(kind: StaticEntityKind, origin: Vec3L, yaw: Float): Boolean {
    val cells = cellsOf(kind, origin, yaw)

    return !index.isTaken(cells) && cells.none { isTerrain(it) || isOccupied(it) }
  }

  /** Puts a standing piece's cells in. Calling it again for the same piece moves it. */
  fun stand(pieceId: Long, kind: StaticEntityKind, origin: Vec3L, yaw: Float) {
    val changed = index.put(pieceId, cellsOf(kind, origin, yaw))
    chunkService.structuresChanged(changed)
  }

  /** Takes a piece's cells out. Nothing happens for a piece that never stood. */
  fun fall(pieceId: Long) {
    val changed = index.remove(pieceId)
    chunkService.structuresChanged(changed)
  }

  /** A piece placed at a yaw stands at the nearest quarter turn: pieces snap to the grid. */
  private fun cellsOf(kind: StaticEntityKind, origin: Vec3L, yaw: Float): List<StructureCell> {
    return StructureRasterizer.rasterize(catalogue.of(kind), origin, StructureRasterizer.quarterTurnsOf(yaw))
  }

  private fun isTerrain(cell: StructureCell): Boolean {
    if (cell.fill == Occupancy.EMPTY) return false

    val config = chunkService.config
    val at = ChunkCoords.localise(config, cell.x, cell.y, cell.z) ?: return true
    val voxels = chunkService.merged(chunkService.normalise(at.chunk))

    return voxels[at.localX, at.localY, at.localZ].solid
  }

  /** Someone whose body, standing at z and one voxel above it, is where the cell would be filled. */
  private fun isOccupied(cell: StructureCell): Boolean {
    if (cell.fill == Occupancy.EMPTY) return false

    var occupied = false
    aoi.forEachInBox(cell.x, cell.y, cell.z - 1, cell.x, cell.y, cell.z, setOf(AoiLayer.DYNAMIC)) { _, _, _, _ ->
      occupied = true
    }

    return occupied
  }
}
